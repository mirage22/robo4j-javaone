package com.javaone.number42;

import com.javaone.number42.engine.LeftEngine;
import com.javaone.number42.engine.RightEngine;
import com.javaone.number42.unit.PlatformKidsUnit;
import com.robo4j.brick.client.AbstractClient;
import com.robo4j.brick.client.io.ClientException;
import com.robo4j.brick.client.request.RequestProcessorCallable;
import com.robo4j.brick.logging.SimpleLoggingUtil;
import com.robo4j.brick.util.ConstantUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * @author Miro Kopecky (@miragemiko)
 * @since 16.08.2016
 */
public class Number42Main extends AbstractClient {

    private static final int PORT = 8023;

    public static void main(String[] args) {
        SimpleLoggingUtil.print(Number42Main.class, "JavaOne4Kids PORT: " + PORT);
        new Number42Main();
    }

    @SuppressWarnings(value = "unchecked")
    private Number42Main(){
        //Logic how the robot will be initiated
        super(Stream.of(LeftEngine.class, RightEngine.class),
                Stream.empty(),
                Stream.of(PlatformKidsUnit.class));
        SimpleLoggingUtil.print(getClass(), "SERVER starts PORT: " + PORT);


        boolean active = true;

        try(ServerSocket server = new ServerSocket(PORT)){
            while(active){
                Socket request = server.accept();
                Future<String> result = submit(new RequestProcessorCallable(request,
                        getEngineCache(), getSensorCache(), getUnitCache()));
                if(result.get().equals(ConstantUtil.EXIT)){
                    SimpleLoggingUtil.print(getClass(), "IS EXIT");
                    active = false;
                }
            }
        } catch (InterruptedException | ExecutionException | IOException e){
            throw new ClientException("SERVER FAILED", e);
        }
        end();
        SimpleLoggingUtil.print(getClass(), "FINAL END");
        System.exit(0);
    }
}
