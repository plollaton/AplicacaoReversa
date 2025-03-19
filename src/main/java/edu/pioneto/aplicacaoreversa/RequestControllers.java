package edu.pioneto.aplicacaoreversa;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

@RestController
@RequestMapping("/inicio")
public class RequestControllers {

    private final PersonRepository personRepository;
    private final WebClient webClient;

    public RequestControllers(PersonRepository personRepository,
                              WebClient webClient) {
        this.personRepository = personRepository;
        this.webClient = webClient;
    }

    @PostMapping("/criar-registro")
    public String criarRegistro(@RequestBody NewPersonDto newPersonDto,
                                @RequestParam boolean replicar) {
        PersonEntity personEntity = new PersonEntity();
        personEntity.setName(newPersonDto.name());
        personEntity.setSurname(newPersonDto.surname());
        personEntity.setAge(newPersonDto.age());

        personRepository.save(personEntity);
        String response = "Registro criado com sucesso!";
        if (replicar){
            response = webClient.post()
                    .uri(URI.create("http://localhost:8081/inicio/criar-registro?replicar=false"))
                    .bodyValue(newPersonDto)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }

        return response;
    }
}
