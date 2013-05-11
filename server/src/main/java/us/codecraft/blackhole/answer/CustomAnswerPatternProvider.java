package us.codecraft.blackhole.answer;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Address;
import org.xbill.DNS.Type;
import us.codecraft.blackhole.context.RequestContext;
import us.codecraft.blackhole.utils.DoubleKeyMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read the config to patterns and process the request record.
 *
 * @author yihua.huang@dianping.com
 * @date Dec 14, 2012
 */
@Component
public class CustomAnswerPatternProvider implements AnswerProvider {

    private volatile DoubleKeyMap<String, Pattern, String> patterns = new DoubleKeyMap<String, Pattern, String>(new ConcurrentHashMap<String, Map<Pattern, String>>(), LinkedHashMap.class);

    private Logger logger = Logger.getLogger(getClass());

    /**
     * When the address configured as "DO_NOTHING",it will not return any
     * address.
     */
    public static final String DO_NOTHING = "do_nothing";
    private static final String FAKE_MX_PREFIX = "mail.";
    private static final String FAKE_CANME_PREFIX = "cname.";

    @Autowired
    private CustomTempAnswerProvider customTempAnswerProvider;

    /*
     * (non-Javadoc)
     *
     * @see
     * us.codecraft.blackhole.answer.AnswerProvider#getAnswer(java.lang.String,
     * int)
     */
    @Override
    public String getAnswer(String query, int type) {
        if (type == Type.PTR) {
            return null;
        }
        String clientIp = RequestContext.getClientIp();
        Map<Pattern, String> patternsForIp = patterns.get(clientIp);
        if (patternsForIp == null) {
            return null;
        }
        for (Entry<Pattern, String> entry : patternsForIp.entrySet()) {
            Matcher matcher = entry.getKey().matcher(query);
            if (matcher.find()) {
                String answer = entry.getValue();
                if (answer.equals(DO_NOTHING)) {
                    return null;
                }
                if (type == Type.MX) {
                    String fakeMXHost = fakeMXHost(query);
                    customTempAnswerProvider.add(clientIp, fakeMXHost, Type.A, answer);
                    return fakeMXHost;
                }
                if (type == Type.CNAME) {
                    String fakeCNAMEHost = fakeCNAMEHost(query);
                    customTempAnswerProvider.add(clientIp, fakeCNAMEHost, Type.A, answer);
                    return fakeCNAMEHost;
                }
                try {
                    customTempAnswerProvider.add(clientIp, reverseIp(answer), Type.PTR, query);
                } catch (Throwable e) {
                    logger.info("not a ip, ignored");
                }
                return answer;
            }
        }
        return null;
    }

    /**
     * generate a fake MX host
     *
     * @param domain
     * @return
     */
    private String fakeMXHost(String domain) {
        return FAKE_MX_PREFIX + domain;
    }

    /**
     * @param domain
     * @return
     */
    private String fakeCNAMEHost(String domain) {
        return FAKE_CANME_PREFIX + domain;
    }

    private String reverseIp(String ip) {
        int[] array = Address.toArray(ip);
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = array.length - 1; i >= 0; i--) {
            stringBuilder.append(array[i] + ".");
        }
        stringBuilder.append("in-addr.arpa.");
        return stringBuilder.toString();
    }

    /**
     * @param patterns the patterns to set
     */
    public void setPatterns(String ip, Map<Pattern, String> patterns) {
        this.patterns.put(ip, patterns);
    }

    public void setPatterns(DoubleKeyMap<String, Pattern, String> patterns) {
        this.patterns = patterns;
    }

}