package alejandro.salazar.mejia.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserProfileResult {

    private String cookie;
    private List<UserTagEvent> views = new ArrayList<>();
    private List<UserTagEvent> buys = new ArrayList<>();

    public UserProfileResult() {
    }

    public UserProfileResult(String cookie, List<UserTagEvent> views, List<UserTagEvent> buys) {
        this.cookie = cookie;
        this.views = views;
        this.buys = buys;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public List<UserTagEvent> getViews() {
        return views;
    }

    public void setViews(List<UserTagEvent> views) {
        this.views = views;
    }

    public List<UserTagEvent> getBuys() {
        return buys;
    }

    public void setBuys(List<UserTagEvent> buys) {
        this.buys = buys;
    }

    @Override
    public String toString() {
        String viewsStr = views.stream()
                .map(event -> String.format("(%s, %s, %d)", event.getTime(), event.getAction(), event.getProductInfo().getProductId()))
                .collect(Collectors.joining(", "));

        String buysStr = buys.stream()
                .map(event -> String.format("(%s, %s, %d)", event.getTime(), event.getAction(), event.getProductInfo().getProductId()))
                .collect(Collectors.joining(", "));

        return String.format("UserProfileResult{cookie='%s', views=[%s], buys=[%s]}", cookie, viewsStr, buysStr);
    }
}