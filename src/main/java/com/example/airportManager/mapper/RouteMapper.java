package com.example.airportManager.mapper;

import com.example.airportManager.dto.route.RouteCreateDTO;
import com.example.airportManager.dto.route.RouteResponseDTO;
import com.example.airportManager.model.Route;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RouteMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "originAirport", ignore = true)
    @Mapping(target = "destAirport", ignore = true)
    @Mapping(target = "flights", ignore = true)
    Route toEntity(RouteCreateDTO dto);

    @Mapping(target = "originAirportId", source = "originAirport.id")
    @Mapping(target = "originCity", source = "originAirport.city")
    @Mapping(target = "originIata", source = "originAirport.iata")
    @Mapping(target = "destAirportId", source = "destAirport.id")
    @Mapping(target = "destCity", source = "destAirport.city")
    @Mapping(target = "destIata", source = "destAirport.iata")
    RouteResponseDTO toResponse(Route entity);
}
