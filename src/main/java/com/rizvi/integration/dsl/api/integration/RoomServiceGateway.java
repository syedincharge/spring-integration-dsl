package com.rizvi.integration.dsl.api.integration;

import com.rizvi.integration.dsl.api.MeetingRoomBooking;
import com.rizvi.integration.dsl.api.referencedata.MeetingRoom;
import com.rizvi.integration.dsl.api.referencedata.RoomsInfoRepository;
import com.rizvi.integration.dsl.api.referencedata.Timings;
import com.rizvi.integration.dsl.api.referencedata.TimingsRepository;
//import jdk.internal.joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.jpa.dsl.Jpa;
import org.springframework.integration.jpa.support.PersistMode;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableIntegration
@IntegrationComponentScan
@Slf4j
public class RoomServiceGateway {

    @Autowired
    TimingsRepository timingsRepository;

    @Autowired
    RoomsInfoRepository roomsInfoRepository;

    @MessagingGateway(name = "roomService", errorChannel = "roomServiceError")
    public interface RoomService {

        @Gateway(requestChannel = "book.input")
        MeetingRoomBooking bookRoom(MeetingRoomBooking meetingRoomBooking);
    }


    @Bean
    public IntegrationFlow book(EntityManager entityManager) {
        return f -> f
                .handle(Jpa.updatingGateway(entityManager).entityClass(MeetingRoomBooking.class)
                        .persistMode(PersistMode.MERGE), e -> e.transactional())
                .<MeetingRoomBooking>handle((p, h) -> {
                    Optional<Timings> timings = timingsRepository.findByRoomIdAndTimeId(p.getRoomInfo().getRoomId(), p.getRoomInfo().getTimeId());
                    Optional<MeetingRoom> room = roomsInfoRepository.findById(p.getRoomInfo().getRoomId());
                    if(room.isPresent() && timings.isPresent()) {
                        var roomTimings = timings.get();
                        roomTimings.setIsAvailable(false);
                        timingsRepository.save(roomTimings);
                    }
                    log.info("{} for Time Slot {} is booked.", room.get().getName(), timings.get().getTimeSlot());
                    return p;
                })
                .<MeetingRoomBooking>handle((p, h) -> p)
                .bridge();
    }



}
