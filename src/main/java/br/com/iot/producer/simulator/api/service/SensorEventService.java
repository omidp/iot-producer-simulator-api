package br.com.iot.producer.simulator.api.service;

import br.com.iot.producer.simulator.api.controller.events.request.SensorEventRequest;
import br.com.iot.producer.simulator.api.model.event.RandomSensorEvent;
import br.com.iot.producer.simulator.api.model.event.SensorEvent;
import br.com.iot.producer.simulator.api.stream.producer.SensorEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SensorEventService {

    private static final Logger LOG = LoggerFactory.getLogger(SensorEventService.class);

    private final SensorEventProducer sensorEventProducer;

    public SensorEventService(SensorEventProducer sensorEventProducer) {
        this.sensorEventProducer = sensorEventProducer;
    }

    public ParallelFlux<Void> produceEvents(List<SensorEventRequest> events) {
        return Flux.fromIterable(events)
                .doFirst(() -> LOG.debug("=== Starting event generator. -> {} ", LocalDateTime.now()))
                .parallel(events.size())
                .runOn(Schedulers.boundedElastic())
                .flatMap(this::processEventCluster)
                .doOnComplete(() -> LOG.info("==== Event generation ended {}", LocalDateTime.now()));
    }

    protected ParallelFlux<Void> processEventCluster(SensorEventRequest request) {
        return Flux.range(0, request.getClusterSize())
                .doFirst(() -> LOG.debug("==== Going to process event cluster -> {}", request))
                .parallel(request.getClusterSize())
                .runOn(Schedulers.boundedElastic())
                .flatMap(integer -> processEvent(request, integer))
                .doOnComplete(() -> LOG.debug("==== Ended event process for cluster -> {}", request));
    }

    protected Flux<Void> processEvent(SensorEventRequest request, Integer clusterId) {
        final SensorEvent sensorEvent = new RandomSensorEvent(request.getType());
        return Flux.range(0, request.getTotal())
                .doFirst(() -> LOG.debug("==== [cluster:{}] Going to process event single event -> {}", clusterId, sensorEvent))
                .delayElements(Duration.ofSeconds(request.getHeartBeat()))
                .flatMap(processSequence -> sendEvent(sensorEvent, clusterId))
                .doOnComplete(() -> LOG.debug("==== [cluster:{}] Going to process event single event -> {}", clusterId, sensorEvent));
    }

    protected Mono<Void> sendEvent(SensorEvent sensorEvent, Integer clusterId) {
        return sensorEventProducer.sendEvent(sensorEvent)
                .doFirst(() -> LOG.debug("==== [cluster:{}] Sending event {}", clusterId, sensorEvent));
    }
}