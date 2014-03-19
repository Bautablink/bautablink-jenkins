package com.bautabits.bautabit;

import com.bautabits.bautabit.dto.PinConfiguration;
import org.junit.Test;
import com.bautabits.bautabit.dto.BautabitInfo;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BautabitTest {

    @Test
    public void helloWorld() throws Exception {
        System.out.println("Discover");
        List<Bautabit> bautabits = Bautabit.discover();
        if (bautabits == null || bautabits.isEmpty())
            System.err.println("No bautabit found");
        else {
            for (Bautabit bautabit : bautabits) {
                System.out.println(bautabit.getResource().getURI());
                BautabitInfo info = bautabit.fetchInfo();
                System.out.println("ID " + info.getId());
                System.out.println("Type " + info.getType());
                /*
                bautabit.setPin("red");
                Thread.sleep(2000);
                bautabit.clearPin("red");
                bautabit.setPin("green");
                Thread.sleep(2000);
                bautabit.clearPin("green");
                bautabit.configurePin("blue", PinConfiguration.blink(0.1f, 0.1f));
                bautabit.setPin("blue");
                Thread.sleep(2000);
                bautabit.clearPin("blue");
*/
                HashMap<String, PinConfiguration> config = new HashMap<String, PinConfiguration>();
                config.put("red", PinConfiguration.blink(0.1f, 0.1f));
                config.put("green", PinConfiguration.blink(0.1f, 0.1f));
                config.put("blue", PinConfiguration.out());
                bautabit.configureNamedPins(config);
                bautabit.setPins(new HashMap<String, Boolean>() {{
                    put("red", true);
                    put("green", false);
                    put("blue", true);
                }});
                Thread.sleep(2000);
                bautabit.clearPin("blue");
                Thread.sleep(1000);
                bautabit.clearPin("red");
            }
        }
    }

    @Test
    public void incompleteUrl_isCompleted() {
        Bautabit bautabit;
        bautabit = new Bautabit("foo.example.com");
        assertEquals("http://foo.example.com:"+ Bautabit.DEFAULT_PORT, bautabit.getResource().getURI().toString());
    }
}
