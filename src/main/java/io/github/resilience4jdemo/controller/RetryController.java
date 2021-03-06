package io.github.resilience4jdemo.controller;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4jdemo.connector.RetryConnector;
import io.github.resilience4jdemo.exceptions.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@RestController
@RequestMapping("/retry")
public class RetryController {

    private RetryConnector connector;

    private Retry retry;

    public RetryController(RetryConnector connector) {
        this.connector = connector;

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1000))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2D))
                .retryOnException(throwable -> throwable instanceof HttpServerErrorException)
                .retryOnResult(result -> result.equals("failure"))
                .ignoreExceptions(BaseException.class)
                .retryExceptions(HttpServerErrorException.class)
                .build();
        RetryRegistry registry = RetryRegistry.ofDefaults();

        retry = registry.retry("retryDecorator", retryConfig);
        TaggedRetryMetrics
                .ofRetryRegistry(registry)
                .bindTo(new SimpleMeterRegistry());
    }

    @GetMapping("/failure")
    public ResponseEntity failure() throws Throwable {
        return ResponseEntity.ok(connector.failure());
    }

    @GetMapping("/failure-decorator")
    public ResponseEntity failureWithDecorator() throws Throwable {
        Supplier<String> retryableFunction = Retry.decorateSupplier(retry, connector::failure);
        return ResponseEntity.ok(retryableFunction.get());
    }
}
