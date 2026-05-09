package com.example.dueltower.content;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S901_InstalledFieldBuff;
import com.example.dueltower.content.status.sdb.S902_SummonFieldAura;
import com.example.dueltower.content.status.sdb.player.tig.Tig202_Status;
import com.example.dueltower.content.status.sdb.player.tig.Tig203_Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusContentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기본 상태 목록 API는 내부 구현용 및 테스트용 상태를 제외한다")
    void statusListExcludesHiddenStatuses() throws Exception {
        mockMvc.perform(get("/api/content/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItems(
                        Tig202_Status.ID,
                        Tig203_Status.ID,
                        S901_InstalledFieldBuff.ID,
                        S902_SummonFieldAura.ID
                ))))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("전체 상태 목록 API는 내부 구현용 및 테스트용 상태를 포함한다")
    void statusAllListIncludesHiddenStatuses() throws Exception {
        mockMvc.perform(get("/api/content/statuses/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems(
                        Tig202_Status.ID,
                        Tig203_Status.ID,
                        S901_InstalledFieldBuff.ID,
                        S902_SummonFieldAura.ID
                )))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("숨겨진 상태는 ID 직접 조회로 접근 가능하다")
    void hiddenStatusDetailStillWorks() throws Exception {
        mockMvc.perform(get("/api/content/statuses/{id}", Tig202_Status.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Tig202_Status.ID))
                .andExpect(jsonPath("$.visibility").value("IMPLEMENTATION"))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.TIG));

        mockMvc.perform(get("/api/content/statuses/{id}", S901_InstalledFieldBuff.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(S901_InstalledFieldBuff.ID))
                .andExpect(jsonPath("$.visibility").value("TEST"))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.COMMON));
    }
}
