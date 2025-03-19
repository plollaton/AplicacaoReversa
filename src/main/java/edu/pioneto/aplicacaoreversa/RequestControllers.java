package edu.pioneto.aplicacaoreversa;

import io.micrometer.observation.Observation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/inicio")
public class RequestControllers {

    private final PersonRepository personRepository;
    private final WebClient webClient;
    private final Tracer tracer;

    private Tracer tracer2 = GlobalOpenTelemetry.getTracer("RequestControllers");

    public RequestControllers(PersonRepository personRepository,
                              WebClient webClient,
                              Tracer tracer
    ) {
        this.personRepository = personRepository;
        this.webClient = webClient;
        this.tracer = tracer;
    }

    @PostMapping("/criar-registro")
    public String criarRegistro(@RequestHeader Map<String, String> headers,
                                @RequestBody NewPersonDto newPersonDto,
                                @RequestParam boolean replicar) {

        Context extractedContext = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), headers, getter);

        Span span = tracer.spanBuilder("criar-registro")
                .setParent(extractedContext)
//                .setSpanKind(SpanK)
        .startSpan();

        try (Scope scope = span.makeCurrent()) {
            PersonEntity personEntity = new PersonEntity();
            personEntity.setName(newPersonDto.name());
            personEntity.setSurname(newPersonDto.surname());
            personEntity.setAge(newPersonDto.age());

            personRepository.save(personEntity);
            String response = "Registro criado com sucesso!";


            Context context = Context.current();
            Map<String, String> headersMap = new HashMap<>();
            GlobalOpenTelemetry.getPropagators()
                    .getTextMapPropagator()
                    .inject(context, headersMap, (carrier, key, value) -> carrier.put(key, value));

            String resp = webClient.get()
                    .uri(URI.create("http://localhost:8081/inicio/consulta-extra"))
                    .headers(httpHeaders -> headersMap.forEach(httpHeaders::set))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            response += resp;

            return response;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            return e.getMessage();
        } finally {
            span.end();
        }
    }

    @GetMapping("/consulta-extra")
    public String consultaExtra(@RequestHeader Map<String, String> headers) {

        Context extractedContext = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), headers, getter);

        Span span = tracer.spanBuilder("consulta-extra")
                .setParent(extractedContext)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(2000);
            return "Consulta extra";
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            return e.getMessage();
        } finally {
            span.end();
        }
    }

    TextMapGetter<Map<String, String>> getter = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null || key == null) {
                return null;
            }
            return carrier.get(key);
        }
    };
}
