package com.skyrpg.vfx.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.skyrpg.vfx.SkyRpgVfx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * Every loaded effect and binding, parsed and linked (effects that play other effects point at them). Built on the
 * server from data packs and on each client from what the server sent; never changed after it's built, so a reload
 * just swaps in a new one.
 *
 * @param effectJson  the source JSON of every effect that parsed, compact, for syncing to clients
 * @param bindingJson the same for bindings
 * @param problems    errors (file skipped) and warnings, each starting with the file's id
 */
public record VfxLibrary(Map<Identifier, EffectDef> effects, Map<Identifier, BindingDef> bindings, Map<Identifier, String> effectJson,
                         Map<Identifier, String> bindingJson, List<String> problems, int errors) {
    /** How deep effects may play other effects. */
    public static final int MAX_DEPTH = 4;
    public static final VfxLibrary EMPTY = new VfxLibrary(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), 0);

    public EffectDef effect(Identifier id) {
        return effects.get(id);
    }

    public static VfxLibrary build(Map<Identifier, JsonElement> effectFiles, Map<Identifier, JsonElement> bindingFiles) {
        List<String> problems = new ArrayList<>();
        int errors = 0;
        Map<Identifier, EffectDef> effects = new LinkedHashMap<>();
        Map<Identifier, String> effectJson = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> file : new TreeMap<>(effectFiles).entrySet()) {
            List<String> warnings = new ArrayList<>();
            try {
                effects.put(file.getKey(), VfxParser.effect(file.getKey(), file.getValue(), warnings));
                effectJson.put(file.getKey(), file.getValue().toString());
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
                problems.add("effect " + file.getKey() + ": " + e.getMessage() + " (skipped)");
                errors++;
            }
            warnings.forEach(w -> problems.add("effect " + file.getKey() + ": " + w));
        }
        errors += link(effects, problems);

        Map<Identifier, BindingDef> bindings = new LinkedHashMap<>();
        Map<Identifier, String> bindingJson = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> file : new TreeMap<>(bindingFiles).entrySet()) {
            List<String> warnings = new ArrayList<>();
            try {
                BindingDef binding = VfxParser.binding(file.getKey(), file.getValue(), warnings);
                binding.effect = effects.get(binding.effectId);
                if (binding.effect == null) {
                    throw new JsonParseException("unknown effect " + binding.effectId);
                }
                bindings.put(file.getKey(), binding);
                bindingJson.put(file.getKey(), file.getValue().toString());
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
                problems.add("binding " + file.getKey() + ": " + e.getMessage() + " (skipped)");
                errors++;
            }
            warnings.forEach(w -> problems.add("binding " + file.getKey() + ": " + w));
        }
        return new VfxLibrary(Collections.unmodifiableMap(effects), Collections.unmodifiableMap(bindings),
                Collections.unmodifiableMap(effectJson), Collections.unmodifiableMap(bindingJson), List.copyOf(problems), errors);
    }

    /** Points "effect" emitters at their effects; drops links that are missing or loop back. Returns the error count. */
    private static int link(Map<Identifier, EffectDef> effects, List<String> problems) {
        int errors = 0;
        for (EffectDef effect : effects.values()) {
            for (EmitterDef emitter : effect.emitters()) {
                if (emitter.mode == EmitterDef.OptionMode.EFFECT) {
                    emitter.effect = effects.get(emitter.effectId);
                    if (emitter.effect == null) {
                        problems.add("effect " + effect.id() + ": plays unknown effect " + emitter.effectId + " (that emitter is off)");
                        errors++;
                    }
                }
            }
        }
        for (EffectDef effect : effects.values()) {
            Set<Identifier> path = new HashSet<>();
            errors += breakCycles(effect, path, problems);
        }
        return errors;
    }

    private static int breakCycles(EffectDef effect, Set<Identifier> path, List<String> problems) {
        int errors = 0;
        path.add(effect.id());
        for (EmitterDef emitter : effect.emitters()) {
            if (emitter.effect == null) {
                continue;
            }
            if (path.contains(emitter.effect.id())) {
                problems.add("effect " + effect.id() + ": playing " + emitter.effect.id() + " would loop forever (that emitter is off)");
                emitter.effect = null;
                errors++;
            } else {
                errors += breakCycles(emitter.effect, path, problems);
            }
        }
        path.remove(effect.id());
        return errors;
    }

    /** Logs the problems of a fresh build. */
    public void log(String side) {
        SkyRpgVfx.LOGGER.info("[{}] Loaded {} VFX effects and {} bindings", side, effects.size(), bindings.size());
        for (String problem : problems) {
            SkyRpgVfx.LOGGER.warn("[{}] VFX {}", side, problem);
        }
    }
}
