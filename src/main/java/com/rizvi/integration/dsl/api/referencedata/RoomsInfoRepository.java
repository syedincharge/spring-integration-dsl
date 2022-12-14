package com.rizvi.integration.dsl.api.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomsInfoRepository extends JpaRepository<MeetingRoom, String>, JpaSpecificationExecutor<MeetingRoom> {
}
