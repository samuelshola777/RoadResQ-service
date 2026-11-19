package com.roadresq.roadresq.rescueRequest.dispatch;

import com.roadresq.roadresq.rescueRequest.RescueRequestRepository;
import com.roadresq.roadresq.rescueRequest.RescueRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RescueDispatchWorker {

    private final RescueRequestRepository rescueRequestRepository;
    private final RescueDispatchService rescueDispatchService;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1_000)
    public void publishDueDispatches() {
        Instant now = Instant.now();
        var due = rescueRequestRepository
                .findTop50ByStatusAndDispatchDueAtLessThanEqualAndDispatchPublishAfterLessThanEqualOrderByDispatchDueAtAsc(
                        RescueRequestStatus.SEARCHING, now, now);

        for (var request : due) {
            Instant attemptAt = Instant.now();
            if (!request.getSearchExpiresAt().isAfter(attemptAt)) {
                rescueRequestRepository.expireSearch(request.getId(), attemptAt);
                continue;
            }

            // Leave the job due until an offer round is saved. The short lease recovers failed publishes and workers.
            if (rescueRequestRepository.claimDispatchPublication(request.getId(), request.getDispatchRound(),
                    attemptAt, attemptAt.plusSeconds(8)) == 0) {
                continue;
            }

            var correlation = new CorrelationData();
            try {
                rabbitTemplate.convertAndSend("", RescueDispatchConfig.QUEUE,
                        request.getId() + ":" + request.getDispatchRound(), correlation);
                var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned() != null) {
                    log.warn("Rescue dispatch message was not routed for request {}", request.getId());
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("Rescue dispatch publish failed for request {}: {}", request.getId(),
                        exception.getClass().getSimpleName());
                return;
            }
        }
    }

    @RabbitListener(queues = RescueDispatchConfig.QUEUE)
    public void processDispatch(String message) {
        int separator = message.lastIndexOf(':');
        rescueDispatchService.dispatch(message.substring(0, separator),
                Integer.parseInt(message.substring(separator + 1)));
    }

    @Scheduled(fixedDelay = 1_000)
    public void releaseExpiredReservations() {
        rescueRequestRepository.expireVendorReservations(Instant.now());
    }
}