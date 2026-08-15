package com.loot.config;

import com.loot.controller.tournament.TournamentController;
import com.loot.controller.tournament.TournamentMapperImpl;
import com.loot.domain.model.ApiKey;
import com.loot.domain.repository.ApiKeyRepository;
import com.loot.domain.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves ApiKeyAuthFilter is actually wired into the real SecurityConfig
 * chain, not just correct in isolation (see ApiKeyAuthFilterTest). */
@WebMvcTest(TournamentController.class)
@Import({SecurityConfig.class, TournamentMapperImpl.class})
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentRepository tournamentRepository;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @Test
    void rejectsRequestWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsRequestWithValidApiKey() throws Exception {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setActive(true);
        key.setCreatedAt(Instant.now());
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(key));
        when(tournamentRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/tournaments").header("X-API-Key", "valid-key"))
                .andExpect(status().isOk());
    }
}
