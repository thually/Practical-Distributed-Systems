package alejandro.salazar.mejia;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import alejandro.salazar.mejia.dao.UserDao;
import alejandro.salazar.mejia.domain.Action;
import alejandro.salazar.mejia.domain.Aggregate;
import alejandro.salazar.mejia.domain.AggregatesQueryResult;
import alejandro.salazar.mejia.domain.UserProfileResult;
import alejandro.salazar.mejia.domain.UserTagEvent;

@RestController
public class FrontComponent {

    @Autowired
    private UserDao userDao;

    private static final Logger log = LoggerFactory.getLogger(FrontComponent.class);

    @PostMapping("/user_tags")
    public ResponseEntity<Void> addUserTag(@RequestBody(required = false) UserTagEvent userTag) {

        try {
            userDao.addUserTag(userTag);
        } catch (Exception e) {
            // server error
            log.error("Error while adding user tag event: {}", userTag, e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/user_profiles/{cookie}")
    public ResponseEntity<UserProfileResult> getUserProfile(@PathVariable("cookie") String cookie,
            @RequestParam("time_range") String timeRangeStr,
            @RequestParam(defaultValue = "200") int limit,
            @RequestBody(required = false) UserProfileResult expectedResult) {

        try {
            UserProfileResult result = userDao.getUserProfile(cookie, timeRangeStr, limit, expectedResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // server error
            log.error("Error while getting user profile for cookie: {}", cookie, e);
            return ResponseEntity.internalServerError().build();
        }

    }

    @PostMapping("/aggregates")
    public ResponseEntity<AggregatesQueryResult> getAggregates(@RequestParam("time_range") String timeRangeStr,
            @RequestParam("action") Action action,
            @RequestParam("aggregates") List<Aggregate> aggregates,
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "brand_id", required = false) String brandId,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestBody(required = false) AggregatesQueryResult expectedResult) {


        // AggregatesQueryResult result = userDao.getAggregates(timeRangeStr, action, aggregates, origin, brandId, categoryId, expectedResult);
        try {
            AggregatesQueryResult result = userDao.getAggregates(timeRangeStr, action, aggregates, origin, brandId, categoryId, expectedResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // server error
            log.error("Error while getting aggregates", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
