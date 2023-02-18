package su.plo.voice.whisper;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import su.plo.config.Config;
import su.plo.config.ConfigField;
import su.plo.config.ConfigValidator;

import java.util.function.Predicate;

@Config
@Data
@Accessors(fluent = true)
public final class WhisperConfig {

    @ConfigField(comment = "Supported values: [1-100]")
    @ConfigValidator(value = PercentValidator.class, allowed = "1-100")
    private int proximityPercent = 50;

    @ConfigField(path = "activation_weight")
    private int activationWeight = 11;

    @ConfigField(path = "sourceline_weight")
    private int sourceLineWeight = 11;

    @ConfigField
    private int visualizeDistanceHexColor = 0x81ecec;

    @NoArgsConstructor
    public static class PercentValidator implements Predicate<Object> {

        @Override
        public boolean test(Object o) {
            if (!(o instanceof Long)) return false;
            long port = (long) o;
            return port > 0 && port <= 100;
        }
    }
}
