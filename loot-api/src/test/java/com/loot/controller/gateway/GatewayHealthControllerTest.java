package com.loot.controller.gateway;

import com.loot.gateway.orchestration.GatewayHealthRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GatewayHealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayHealthRegistry.class)
class GatewayHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayHealthRegistry healthRegistry;

    @Test
    void reportsHealthyStatusForAGatewayWithGoodSuccessRate() throws Exception {
        for (int i = 0; i < 9; i++) {
            healthRegistry.record("MPESA", true, 100);
        }
        healthRegistry.record("MPESA", false, 500);

        mockMvc.perform(get("/api/v1/gateways/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='MPESA')].status").value("HEALTHY"))
                .andExpect(jsonPath("$[?(@.name=='MPESA')].successRate").value(0.9))
                .andExpect(jsonPath("$[?(@.name=='MPESA')].avgLatencyMs").value(140.0));
    }

    @Test
    void reportsUnhealthyStatusBelowEightyPercentSuccess() throws Exception {
        for (int i = 0; i < 5; i++) {
            healthRegistry.record("FLUTTERWAVE", true);
        }
        for (int i = 0; i < 5; i++) {
            healthRegistry.record("FLUTTERWAVE", false);
        }

        mockMvc.perform(get("/api/v1/gateways/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='FLUTTERWAVE')].status").value("UNHEALTHY"));
    }

    @Test
    void reportsBothKnownGatewaysEvenWithNoData() throws Exception {
        mockMvc.perform(get("/api/v1/gateways/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name=='MPESA')].status").value("HEALTHY"))
                .andExpect(jsonPath("$[?(@.name=='MPESA')].avgLatencyMs").value(-1.0));
    }
}
