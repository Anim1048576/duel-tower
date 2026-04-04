package com.example.dueltower.preset.service;

import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.preset.domain.Preset;
import com.example.dueltower.preset.dto.CreatePresetRequest;
import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.preset.dto.UpdatePresetRequest;
import com.example.dueltower.preset.repository.PresetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PresetService {

    private final PresetRepository presetRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final CardService cardService;
    private final PassiveService passiveService;

    public PresetService(
            PresetRepository presetRepository,
            CharacterProfileRepository characterProfileRepository,
            CardService cardService,
            PassiveService passiveService
    ) {
        this.presetRepository = presetRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.cardService = cardService;
        this.passiveService = passiveService;
    }

    @Transactional(readOnly = true)
    public List<PresetResponse> listMine(String ownerUsername) {
        return presetRepository.findAllByOwnerUsernameOrderByIdAsc(ownerUsername)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PresetResponse getMine(String ownerUsername, long presetId) {
        return toResponse(getOwnedPreset(ownerUsername, presetId));
    }

    @Transactional
    public PresetResponse create(String ownerUsername, CreatePresetRequest req) {
        PresetPayload payload = parseAndValidateCreateRequest(req);
        Preset preset = Preset.create(
                ownerUsername,
                payload.name(),
                payload.characterId(),
                payload.deckCardIds(),
                payload.exCardId(),
                payload.passiveIds()
        );
        return toResponse(presetRepository.save(preset));
    }

    @Transactional
    public PresetResponse update(String ownerUsername, long presetId, UpdatePresetRequest req) {
        PresetPayload payload = parseAndValidateUpdateRequest(req);

        Preset preset = getOwnedPreset(ownerUsername, presetId);
        applyPayload(preset, payload);
        return toResponse(preset);
    }

    @Transactional
    public PresetResponse cloneMine(String ownerUsername, long presetId) {
        Preset source = getOwnedPreset(ownerUsername, presetId);
        Preset clone = Preset.create(
                ownerUsername,
                buildCloneName(source.getName()),
                source.getCharacterId(),
                source.getDeckCardIds(),
                source.getExCardId(),
                source.getPassiveIds()
        );
        return toResponse(presetRepository.save(clone));
    }

    @Transactional
    public void delete(String ownerUsername, long presetId) {
        Preset preset = getOwnedPreset(ownerUsername, presetId);
        presetRepository.delete(preset);
    }

    @Transactional(readOnly = true)
    public PresetLoadout getOwnedLoadout(String ownerUsername, long presetId) {
        Preset preset = getOwnedPreset(ownerUsername, presetId);
        return toLoadout(preset);
    }

    /**
     * owner scope 강제 조회 helper.
     * - 다음 단계 clone/apply에서도 동일하게 사용한다.
     */
    private Preset getOwnedPreset(String ownerUsername, long presetId) {
        return presetRepository.findByIdAndOwnerUsername(presetId, ownerUsername)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "preset not found: " + presetId));
    }

    private PresetPayload parseAndValidateCreateRequest(CreatePresetRequest req) {
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        return validateAndNormalizePayload(req.name(), req.characterId(), req.deckCardIds(), req.exCardId(), req.passiveIds());
    }

    private PresetPayload parseAndValidateUpdateRequest(UpdatePresetRequest req) {
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        return validateAndNormalizePayload(req.name(), req.characterId(), req.deckCardIds(), req.exCardId(), req.passiveIds());
    }

    private PresetPayload validateAndNormalizePayload(
            String name,
            Long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
        String normalizedName = requireText(name, "name is required");
        if (characterId == null || characterId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "characterId must be positive");
        }
        if (!characterProfileRepository.existsById(characterId)) {
            throw new ResponseStatusException(BAD_REQUEST, "unknown characterId: " + characterId);
        }

        String normalizedExCardId = requireText(exCardId, "exCardId is required");
        if (!isKnownCardId(normalizedExCardId)) {
            throw new ResponseStatusException(BAD_REQUEST, "unknown exCardId: " + normalizedExCardId);
        }

        List<String> normalizedDeckCardIds = validateAndNormalizeDeckCardIds(deckCardIds);
        List<String> normalizedPassiveIds = validateAndNormalizePassiveIds(passiveIds);
        return new PresetPayload(
                normalizedName,
                characterId,
                normalizedDeckCardIds,
                normalizedExCardId,
                normalizedPassiveIds
        );
    }

    private List<String> validateAndNormalizeDeckCardIds(List<String> deckCardIds) {
        if (deckCardIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "deckCardIds is required");
        }
        List<String> ids = deckCardIds;
        List<String> normalized = new ArrayList<>(ids.size());
        for (String rawCardId : ids) {
            String cardId = requireText(rawCardId, "deckCardIds must not contain blank values");
            if (!isKnownCardId(cardId)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown cardId: " + cardId);
            }
            normalized.add(cardId);
        }
        return List.copyOf(normalized);
    }

    private List<String> validateAndNormalizePassiveIds(List<String> passiveIds) {
        if (passiveIds == null) {
            throw new ResponseStatusException(BAD_REQUEST, "passiveIds is required");
        }
        List<String> ids = passiveIds;
        Set<String> knownPassives = passiveService.defsMap().keySet();
        List<String> normalized = new ArrayList<>(ids.size());
        for (String rawPassiveId : ids) {
            String passiveId = requireText(rawPassiveId, "passiveIds must not contain blank values");
            if (!knownPassives.contains(passiveId)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown passiveId: " + passiveId);
            }
            normalized.add(passiveId);
        }
        return List.copyOf(normalized);
    }

    private boolean isKnownCardId(String rawCardId) {
        if (rawCardId == null || rawCardId.isBlank()) {
            return false;
        }
        return cardService.asMap().containsKey(new Ids.CardDefId(rawCardId));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return value.trim();
    }

    private void applyPayload(Preset preset, PresetPayload payload) {
        preset.update(
                payload.name(),
                payload.characterId(),
                payload.deckCardIds(),
                payload.exCardId(),
                payload.passiveIds()
        );
    }

    private String buildCloneName(String sourceName) {
        String suffix = " (copy)";
        String candidate = sourceName + suffix;
        if (candidate.length() <= 100) {
            return candidate;
        }
        int baseLimit = 100 - suffix.length();
        if (baseLimit <= 0) {
            return suffix.substring(0, 100);
        }
        return sourceName.substring(0, Math.min(sourceName.length(), baseLimit)) + suffix;
    }

    /**
     * 확장 포인트 안내:
     * - clone endpoint가 생기면 getOwnedPreset(...)으로 원본 조회 후 payload 복제 후 이름만 조정해 저장.
     * - session apply endpoint가 생기면 getOwnedPreset(...)+toResponse(...) 사이 DTO/세션 변환을 추가.
     */
    @SuppressWarnings("unused")
    private Preset clonePreset(String ownerUsername, long presetId) {
        Preset source = getOwnedPreset(ownerUsername, presetId);
        PresetPayload payload = new PresetPayload(
                source.getName(),
                source.getCharacterId(),
                source.getDeckCardIds(),
                source.getExCardId(),
                source.getPassiveIds()
        );
        return Preset.create(
                ownerUsername,
                payload.name(),
                payload.characterId(),
                payload.deckCardIds(),
                payload.exCardId(),
                payload.passiveIds()
        );
    }

    private PresetResponse toResponse(Preset preset) {
        return new PresetResponse(
                preset.getId(),
                preset.getOwnerUsername(),
                preset.getName(),
                preset.getCharacterId(),
                List.copyOf(new ArrayList<>(preset.getDeckCardIds())),
                preset.getExCardId(),
                List.copyOf(new ArrayList<>(preset.getPassiveIds())),
                preset.getCreateDate(),
                preset.getUpdateDate()
        );
    }

    private PresetLoadout toLoadout(Preset preset) {
        return new PresetLoadout(
                preset.getCharacterId(),
                preset.getPassiveIds(),
                preset.getDeckCardIds(),
                preset.getExCardId()
        );
    }

    private record PresetPayload(
            String name,
            Long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
    }

    public record PresetLoadout(
            Long characterId,
            List<String> passiveIds,
            List<String> deckCardIds,
            String exCardId
    ) {
    }
}
