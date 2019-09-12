package com.mynotes.spring.cloud.zuul;

import com.mynotes.spring.cloud.zuul.filters.SampleErrorFilter;
import com.mynotes.spring.cloud.zuul.filters.SimpleFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableZuulProxy
public class ZuulApplication {

    @Bean
    public SimpleFilter simpleFilter() {
        return new SimpleFilter();
    }



    public static void main(String[] args) {
        SpringApplication.run(ZuulApplication.class, args);
    }


}
