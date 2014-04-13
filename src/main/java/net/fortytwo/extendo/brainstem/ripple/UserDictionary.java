package net.fortytwo.extendo.brainstem.ripple;

import net.fortytwo.ripple.model.RippleValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class UserDictionary {
    private Map<String, RippleValue> map = new HashMap<String, RippleValue>();

    public RippleValue get(final String symbol) {
        return map.get(symbol);
    }

    public RippleValue put(final String symbol,
                           final RippleValue value) {
        RippleValue existing = map.get(symbol);
        if (null == existing) {
            map.put(symbol, value);
            return value;
        } else {
            return existing;
        }
    }

    public void remove(final String symbol) {
        map.remove(symbol);
    }
}
