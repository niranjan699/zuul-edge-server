package com.mynotes.spring.cloud.zuul.filters;

import com.netflix.client.ClientException;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.SocketTimeoutException;

public class SampleErrorFilter extends ZuulFilter {

    private static final String SEND_ERROR_FILTER_RAN = "sendErrorFilter.ran";

    private static Logger log = LoggerFactory.getLogger(SimpleFilter.class);

    @Value("${error.path:/error}")
    private String errorPath;

    @Override
    public String filterType() {
        return FilterConstants.ERROR_TYPE;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        // only forward to errorPath if it hasn't been forwarded to already
        return ctx.getThrowable() != null;
    }

    @Override
    public int filterOrder() {
        return -1;
    }

    @Override
    public Object run() {

        try {
            RequestContext      ctx      = RequestContext.getCurrentContext();
            HttpServletResponse response = ctx.getResponse();
           // addAccessControlHeaders(response);
           // response.addHeader(CORRELATION_ID_KEY, correlationIdFromCtx());
           // response.addHeader(REQUEST_ID_KEY, requestIdFromCtx());
            ExceptionHolder    exception = findZuulException(ctx.getThrowable());
            HttpServletRequest request   = ctx.getRequest();

            if(exception.getErrorCause().contains("dms.AuthFilter")){
                request.setAttribute("javax.servlet.error.status_code", 401);
                log.info("Illegal Access stopped at {}", exception.getErrorCause());
                request.setAttribute("javax.servlet.error.message", "DMS Auth Failure");
            }
            else if (exception.getErrorCause().contains("AuthFilter")) {
                request.setAttribute("javax.servlet.error.status_code", 401);
                log.info("Illegal Access stopped at {}", exception.getErrorCause());
                request.setAttribute("javax.servlet.error.message", "Token doesn't exist or is invalid");
            } else {
                request.setAttribute("javax.servlet.error.status_code", exception.getStatusCode());
                log.warn("Error during filtering", exception.getThrowable());
                request.setAttribute("javax.servlet.error.exception", exception.getThrowable());
                if ( StringUtils.hasText(exception.getErrorCause())) {
                    request.setAttribute("javax.servlet.error.message", exception.getErrorCause());
                }
            }
            log.error("Error:",exception.getThrowable());
            RequestDispatcher dispatcher = request.getRequestDispatcher(this.errorPath);
            if (dispatcher != null) {
                ctx.set(SEND_ERROR_FILTER_RAN, true);
                if (!ctx.getResponse().isCommitted()) {
                    ctx.setResponseStatusCode(exception.getStatusCode());
                    dispatcher.forward(request, ctx.getResponse());
                }
            }
        } catch (Exception ex) {
            ReflectionUtils.rethrowRuntimeException(ex);
        }
        return null;
    }

    private ExceptionHolder findZuulException(Throwable throwable) {
        if (throwable.getCause() instanceof ZuulRuntimeException ) {
            Throwable cause = null;
            if (throwable.getCause().getCause() != null) {
                cause = throwable.getCause().getCause().getCause();
            }
            if (cause instanceof ClientException
                    && cause.getCause() != null
                    && cause.getCause().getCause() instanceof SocketTimeoutException ) {

                ZuulException zuulException =
                        new ZuulException("", 504, ZuulException.class.getName() + ": Hystrix Readed time out");
                return new SampleErrorFilter.ZuulExceptionHolder(zuulException);
            }
            // this was a failure initiated by one of the local filters
            if (throwable.getCause().getCause() instanceof ZuulException) {
                return new ZuulExceptionHolder((ZuulException) throwable.getCause().getCause());
            }
        }

        if (throwable.getCause() instanceof ZuulException) {
            // wrapped zuul exception
            return new ZuulExceptionHolder((ZuulException) throwable.getCause());
        }

        if (throwable instanceof ZuulException) {
            // exception thrown by zuul lifecycle
            return new ZuulExceptionHolder((ZuulException) throwable);
        }

        // fallback
        return new SampleErrorFilter.DefaultExceptionHolder(throwable);
    }

    public void setErrorPath(String errorPath) {
        this.errorPath = errorPath;
    }

    protected interface ExceptionHolder {

        Throwable getThrowable();

        default int getStatusCode() {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }

        default String getErrorCause() {
            return null;
        }
    }

    protected static class DefaultExceptionHolder implements ExceptionHolder {

        private final Throwable throwable;

        DefaultExceptionHolder(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public Throwable getThrowable() {
            return this.throwable;
        }
    }

    protected static class ZuulExceptionHolder implements ExceptionHolder {

        private final ZuulException exception;

        public ZuulExceptionHolder(ZuulException exception) {
            this.exception = exception;
        }

        @Override
        public Throwable getThrowable() {
            return this.exception;
        }

        @Override
        public int getStatusCode() {
            return this.exception.nStatusCode;
        }

        @Override
        public String getErrorCause() {
            return this.exception.errorCause;
        }
    }
}
