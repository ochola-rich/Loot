package com.loot.mapping;

import org.mapstruct.factory.Mappers;
import org.mapstruct.Mapper;

@Mapper
interface SampleMapper {

    SampleMapper INSTANCE = Mappers.getMapper(SampleMapper.class);

    SampleDestination toDestination(SampleSource source);
}
