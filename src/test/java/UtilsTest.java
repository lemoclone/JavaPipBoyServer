import de.luckydonald.utils.ObjectWithLogger;
import org.junit.Test;

import static de.luckydonald.utils.Functions.getMethodName;
import static org.junit.Assert.assertEquals;

/**
 * Created by  on
 *
 * @author luckydonald
 * @since 18.02.2016
 **/
public class UtilsTest extends ObjectWithLogger {
    @Test
    public void testMethodName() {
        assertEquals("getMethodName()", "testMethodName", getMethodName());
    }
}