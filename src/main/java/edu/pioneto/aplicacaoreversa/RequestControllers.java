package edu.pioneto.aplicacaoreversa;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/inicio")
public class RequestControllers {

    private final PersonRepository personRepository;
    private final WebClient webClient;
    private final Tracer tracer;

    Logger logger = LoggerFactory.getLogger("RequestController");

    public RequestControllers(PersonRepository personRepository,
                              WebClient webClient,
                              Tracer tracer) {
        this.personRepository = personRepository;
        this.webClient = webClient;
        this.tracer = tracer;
    }

    @GetMapping("/teste-span")
    public ResponseEntity<String> testeSpan(){

        return new ResponseEntity<>("Registro criado com sucesso!", HttpStatus.OK);
    }

    @PostMapping("/criar-registro")
    public ResponseEntity<String> criarRegistro(@RequestHeader Map<String, String> headers,
                                @RequestBody NewPersonDto newPersonDto,
                                @RequestParam boolean replicar,
                                @RequestParam boolean emitirException) {

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
            MDC.put("span_id", span.getSpanContext().getSpanId());
            logger.info("Registro salvo com sucesso no banco H2");
            String response = "Registro criado com sucesso!";


            Context context = Context.current();
            Map<String, String> headersMap = new HashMap<>();
            GlobalOpenTelemetry.getPropagators()
                    .getTextMapPropagator()
                    .inject(context, headersMap, (carrier, key, value) -> carrier.put(key, value));

            String resp = webClient.get()
                    .uri(URI.create(String.format("http://localhost:8081/inicio/consulta-extra?emitirException=%s", emitirException)))
                    .headers(httpHeaders -> headersMap.forEach(httpHeaders::set))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            response += resp;

            return new ResponseEntity<>("Registro criado com sucesso!", HttpStatus.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } finally {
            span.end();
        }
    }

    @GetMapping("/consulta-extra")
    public ResponseEntity<String> consultaExtra(@RequestHeader Map<String, String> headers,
                                        @RequestParam boolean emitirException) {

        Context extractedContext = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), headers, getter);

        Span span = tracer.spanBuilder("consulta-extra")
                .setParent(extractedContext)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(2000);
            MDC.put("span_id", span.getSpanContext().getSpanId());
            logger.info("Consulta realizada na outra instancia do banco H2");

            if (emitirException){
                throw new Exception("Teste de erro");
            }


            return new ResponseEntity<>("Consulta extra OK", HttpStatus.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            logger.error(e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
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
