package com.example.testeotel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    private final Tracer trace;

    public MainController(OpenTelemetry openTelemetry) {
        this.trace = openTelemetry.getTracer("MainControllerTracer");
    }

    @GetMapping("/")
    public ResponseEntity<String> get(){
//        Span span = trace.spanBuilder("MainControllerSpan")
//                .setSpanKind(SpanKind.INTERNAL)
//                .setAttribute("teste", 1)
//                .startSpan();
//
//        span.setAttribute("teste2", 2);


        Span span = Span.current();
        if (span != null && span.getSpanContext().isValid()){
            System.out.println(span.getSpanContext().getSpanId());
            System.out.println(span.getSpanContext().getTraceId());

            span.setAttribute("teste", "teste texto");
        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}
