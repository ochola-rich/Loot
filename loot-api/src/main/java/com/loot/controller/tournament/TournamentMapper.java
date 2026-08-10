package com.loot.controller.tournament;

import com.loot.domain.model.Tournament;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TournamentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Tournament toEntity(CreateTournamentRequest request);

    TournamentResponse toResponse(Tournament tournament);
}
