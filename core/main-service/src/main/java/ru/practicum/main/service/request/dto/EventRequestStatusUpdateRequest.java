package ru.practicum.main.service.request.dto;

import lombok.Data;
import ru.practicum.main.service.request.model.RequestStatus;

import java.util.List;

@Data
public class EventRequestStatusUpdateRequest {
    private List<Long> requestIds;
    private RequestStatus status;
}