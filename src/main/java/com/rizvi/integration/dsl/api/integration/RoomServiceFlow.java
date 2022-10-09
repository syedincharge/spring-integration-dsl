package com.rizvi.integration.dsl.api.integration;

import com.rizvi.integration.dsl.api.MeetingRoomBooking;
import com.rizvi.integration.dsl.api.referencedata.MeetingRoom;
import com.rizvi.integration.dsl.api.referencedata.RoomsInfoRepository;
import com.rizvi.integration.dsl.api.referencedata.Timings;
import com.rizvi.integration.dsl.api.referencedata.TimingsRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.HttpHeaders;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.jpa.dsl.Jpa;
import org.springframework.messaging.support.MessageBuilder;

import javax.persistence.EntityManager;
import java.util.Optional;

@Configuration
@Slf4j
public class RoomServiceFlow {


    @Autowired
    RoomServiceGateway.RoomService roomServiceGateway;

    @Autowired
    TimingsRepository timingsRepository;

    @Autowired
    RoomsInfoRepository roomsInfoRepository;

    @Bean
    public IntegrationFlow roomsInfoFlow(EntityManager entityManager) {
        return IntegrationFlows.from(Http.inboundGateway("/rooms")
                        .requestMapping(m -> m.methods(HttpMethod.GET))
                        .errorChannel("globalErrorChannel.input"))
                .wireTap("loggingFlow.input")
                .log(LoggingHandler.Level.INFO, this.getClass().getName(), m -> "Retrieving Total Rooms Information")
                .handle(Jpa.retrievingGateway(entityManager).entityClass(MeetingRoom.class))
                .get();
    }

    @Bean
    public IntegrationFlow bookRoomFlow() {
        return IntegrationFlows.from(Http.inboundGateway("/booking")
                        .requestPayloadType(MeetingRoomBooking.class)
                        .requestMapping(m -> m.methods(HttpMethod.POST))
                        .errorChannel("globalErrorChannel.input"))
                .wireTap("loggingFlow.input")
                .log(LoggingHandler.Level.INFO, this.getClass().getName(), m -> "Booking Meeting Room")
                .<MeetingRoomBooking>handle((p,h) -> roomServiceGateway.bookRoom(p))
                .log(LoggingHandler.Level.INFO, this.getClass().getName(), m -> "End - Booking Meeting Room : " +  m.getPayload())
                .transform(MeetingRoomBooking.class, p -> p.getMeetingRoomReference())
                .get();
    }

    @Bean
    public IntegrationFlow releaseRoomFlow() {
        return IntegrationFlows.from(Http.inboundGateway("/release/{roomId}/{timeId}")
                        .headerExpression("roomId", "#pathVariables.roomId")
                        .headerExpression("timeId", "#pathVariables.timeId")
                        .requestMapping(m -> m.methods(HttpMethod.GET))
                        .errorChannel("globalErrorChannel.input"))
                .wireTap("loggingFlow.input")
                .log(LoggingHandler.Level.INFO, this.getClass().getName(), m -> "Releasing Meeting Room")
                .handle((p,h) -> {
                    Optional<Timings> timings = timingsRepository.findByRoomIdAndTimeId(h.get("roomId").toString(),
                            h.get("timeId").toString());
                    Optional<MeetingRoom> room = roomsInfoRepository.findById(h.get("roomId").toString());
                    if(room.isPresent() && timings.isPresent()){
                        Timings roomTiming = timings.get();
                        if(!roomTiming.getIsAvailable())
                            roomTiming.setIsAvailable(true);
                        log.info("{} for Time Slot {} is released." , room.get().getName(), timings.get().getTimeSlot());
                        timingsRepository.save(roomTiming);
                    }
                    return MessageBuilder.withPayload("{}").copyHeadersIfAbsent(h).build();
                })
                .get();
    }



    @Bean
    public IntegrationFlow loggingFlow() {
        return f -> f.handle(message -> {
            log.info("===========================incoming request details================================================");
            log.info("URI         : {}", message.getHeaders().get(HttpHeaders.REQUEST_URL));
            log.info("Method      : {}", message.getHeaders().get(HttpHeaders.REQUEST_METHOD));
            log.info("Request body: {}", message.getPayload());
            log.info("===================================================================================================");
        });
    }
}
