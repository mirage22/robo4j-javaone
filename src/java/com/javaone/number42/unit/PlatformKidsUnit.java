/*
 * Copyright (C) 2016. Miroslav Kopecky
 * This PlatformUnit.java is part of robo4j.
 *
 *     robo4j is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     robo4j is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with robo4j .  If not, see <http://www.gnu.org/licenses/>.
 */

package com.javaone.number42.unit;

import com.robo4j.brick.client.enums.RequestCommandEnum;
import com.robo4j.brick.platform.ClientPlatformConsumer;
import com.robo4j.brick.platform.ClientPlatformException;
import com.robo4j.brick.platform.ClientPlatformProducer;
import com.robo4j.commons.agent.AgentConsumer;
import com.robo4j.commons.agent.AgentProducer;
import com.robo4j.commons.agent.AgentStatus;
import com.robo4j.commons.agent.GenericAgent;
import com.robo4j.commons.agent.ProcessAgent;
import com.robo4j.commons.agent.ProcessAgentBuilder;
import com.robo4j.commons.annotation.RoboUnit;
import com.robo4j.commons.command.GenericCommand;
import com.robo4j.commons.command.RoboUnitCommand;
import com.robo4j.commons.unit.DefaultUnit;
import com.robo4j.lego.control.LegoBrickRemote;
import com.robo4j.lego.control.LegoEngine;
import com.robo4j.lego.control.LegoSensor;
import com.robo4j.lego.control.LegoUnit;
import com.robo4j.lego.enums.LegoEnginePartEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Miro Kopecky (@miragemiko)
 * @since 16.08.2016
 */

@RoboUnit(value = PlatformKidsUnit.UNIT_NAME,
        system = PlatformKidsUnit.SYSTEM_NAME,
        producer = PlatformKidsUnit.PRODUCER_NAME,
        consumer = {"left", "right"})
public class PlatformKidsUnit extends DefaultUnit implements LegoUnit {

    private static final int AGENT_PLATFORM_POSITION = 0;
    private static final String[] CONSUMER_NAME = {"left", "right"};
    static final String UNIT_NAME = "platformUnit";
    static final String SYSTEM_NAME = "legoBrick1";
    static final String PRODUCER_NAME = "default";

    private volatile LinkedBlockingQueue<GenericCommand<RequestCommandEnum>> commandQueue;

    public PlatformKidsUnit() {
    }

    @Override
    public void setExecutor(final ExecutorService executor){
        this.executorForAgents = executor;
    }

    @Override
    public LegoUnit init(final LegoBrickRemote legoBrickRemote,
                         final Map<String, LegoEngine> engineCache,
                         final Map<String, LegoSensor> sensorCache){

        if(Objects.nonNull(executorForAgents)){
            this.agents = new ArrayList<>();
            this.active = new AtomicBoolean(false);
            this.commandQueue = new LinkedBlockingQueue<>();

            final Exchanger<GenericCommand<RequestCommandEnum>> platformExchanger = new Exchanger<>();
            this.agents.add(createAgent("platformAgent",
                    new ClientPlatformProducer(commandQueue,platformExchanger),
                    new ClientPlatformConsumer(executorForAgents, platformExchanger, engineCache.entrySet().stream()
                            .filter(entry -> entry.getValue().getPart().equals(LegoEnginePartEnum.PLATFORM))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))));

            if(!agents.isEmpty()){
                active.set(true);
                logic = initLogic();
            }
        }

        return this;
    }

    @SuppressWarnings(value = "unchecked")
    @Override
    public boolean process(RoboUnitCommand command){
        try {
            GenericCommand<RequestCommandEnum> processCommand = (GenericCommand<RequestCommandEnum>) command;
            commandQueue.put(processCommand);
            ProcessAgent platformAgent = (ProcessAgent) agents.get(AGENT_PLATFORM_POSITION);
            platformAgent.setActive(true);
            platformAgent.getExecutor().execute((Runnable) platformAgent.getProducer());
            final Future<Boolean> engineActive = platformAgent.getExecutor().submit((Callable<Boolean>) platformAgent.getConsumer());
            try {
                platformAgent.setActive(engineActive.get());
            } catch (InterruptedException | ConcurrentModificationException | ExecutionException e) {
                throw new ClientPlatformException("SOMETHING ERROR CYCLE COMMAND= ", e);
            }
            return true;

        } catch (InterruptedException e) {
            throw new ClientPlatformException("PLATFORM COMMAND e= ", e );
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public String getUnitName() {
        return UNIT_NAME;
    }

    @Override
    public String getSystemName() {
        return SYSTEM_NAME;
    }

    @Override
    public String getProducerName() {
        return PRODUCER_NAME;
    }

    @Override
    public String getConsumerName() {
        return Arrays.asList(CONSUMER_NAME).toString();
    }

    @Override
    protected Map<RoboUnitCommand, Function<ProcessAgent, AgentStatus>> initLogic() {
        return null;
    }

    //Protected Methods
    @Override
    protected GenericAgent createAgent(String name, AgentProducer producer, AgentConsumer consumer) {
        return Objects.nonNull(producer) && Objects.nonNull(consumer) ? ProcessAgentBuilder.Builder(executorForAgents)
                .setProducer(producer)
                .setConsumer(consumer)
                .build() : null;
    }
}
