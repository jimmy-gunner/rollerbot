package it.pathfinder.rollerbot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pathfinder.rollerbot.config.ConfigBean;
import it.pathfinder.rollerbot.data.entity.TelegramUser;
import it.pathfinder.rollerbot.data.service.TelegramUserService;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;


import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TelegramBot extends TelegramLongPollingBot
{

    @Autowired
    private ConfigBean configBean;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EndpointsListener endpointsListener;

    @Autowired
    private TelegramUserService telegramUserService;

    private static Logger logger = LogManager.getLogger();

    private WebClient webClient;



    @PostConstruct
    public void init()
    {
        webClient = WebClient.create("http://localhost:8080");
    }

    @Override
    public void onUpdateReceived(Update update)
    {
        Message message = update.getMessage();
        TelegramUser telegramUser = telegramUserService.registerUser(message.getFrom());
        String queryParam = "?user="+telegramUser.getTgId();
        try {
            String response = webClient.get()
                                    .uri(prepareUri(message.getText())+queryParam)
                                    .exchange()
                                    .block()
                                    .bodyToMono(String.class)
                                    .block();
            JSONObject prettyResponse = new JSONObject(response).getJSONObject("data");
            sendMessage(prettyResponse.toString(1), message.getMessageId(), message.getChatId());
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
            sendMessage(e.getMessage(), message.getMessageId(), message.getChatId());
        }
    }

    private void sendMessage(String message, Integer messageId, Long chatId)
    {
        try {
            execute(new SendMessage()
                    .setText(message)
                    .setReplyToMessageId(messageId)
                    .setChatId(chatId));
        } catch (TelegramApiException e) {
            logger.error("Send message fail: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return configBean.getTelegramBotUsername();
    }

    @Override
    public String getBotToken() {
        return configBean.getTelegramBotToken();
    }

    private String prepareUri(String endPoint)
    {
        /*String finalEndPoint = endPoint;
        Optional<Boolean> isApi = endpointsListener.getSupportedEndPoint()
                .stream()
                .map(suppEndPoint -> suppEndPoint.startsWith(finalEndPoint.split(" ")[0]))
                .findAny();

        if (isApi.isPresent() && isApi.get()){
            endPoint = (configBean.getSpringWebservicesPath() + endPoint).replace(" ", "/");
        }*/
        return (configBean.getSpringWebservicesPath() + endPoint).replace(" ", "/");
    }

}

@Data
@Component
class EndpointsListener implements ApplicationListener {

    private List<String> supportedEndPoint;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            ApplicationContext applicationContext = ((ContextRefreshedEvent) event).getApplicationContext();
            applicationContext.getBean(RouterFunctionMapping.class).getRouterFunction();

            supportedEndPoint = Arrays.stream(Objects.requireNonNull(applicationContext.getBean(RouterFunctionMapping.class)
                    .getRouterFunction())
                    .toString().split("\n"))
                        .map(message -> message.split("&& ")[1].split("\\)")[0])
                        .filter(endpoint -> !endpoint.contains("{"))
                    .collect(Collectors.toList());
        }
    }
}