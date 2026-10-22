package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.example.mekanismmagic.integration.forbiddenarcanus.client.ForbiddenJeiLayout.*;

/** Pure geometry contract; this deliberately makes no claim to render JEI without a client. */
public final class ForbiddenJeiLayoutSelfTest {
    public static void main(String[] args) {
        verify(CLIBANO_HEIGHT, List.of(CLIBANO_FIRST, CLIBANO_SECOND, CLIBANO_OUTPUT,
                CLIBANO_FIRE, CLIBANO_ENHANCER, CLIBANO_RESIDUE));
        var forge = new ArrayList<>(List.of(FORGE_MAIN, FORGE_OUTPUT));
        forge.addAll(FORGE_AUXILIARY);
        forge.addAll(FORGE_ENHANCERS);
        verify(FORGE_HEIGHT, forge);
        require(FORGE_AUXILIARY.size() == 8 && FORGE_ENHANCERS.size() == 4, "Forge display lost machine slot coverage");
        require(forge.stream().noneMatch(slot -> slot.name().equals("core")
                        || slot.name().equals("starter")
                        || slot.name().equals("installed_core")),
                "Removed core or hammer is still advertised as a JEI slot");
        System.out.println("Forbidden JEI machine-tier category slot bounds and no-core/hammer contract passed");
    }

    private static void verify(int height, List<Slot> slots) {
        var names = new HashSet<String>();
        for (var slot : slots) {
            require(names.add(slot.name()), "Duplicate slot name: " + slot.name());
            require(slot.x() >= 2 && slot.y() >= 2 && slot.x() + 17 < WIDTH && slot.y() + 17 < height,
                    "Slot frame outside recipe bounds: " + slot.name());
        }
        for (int first = 0; first < slots.size(); first++) {
            for (int second = first + 1; second < slots.size(); second++) {
                var a = slots.get(first);
                var b = slots.get(second);
                require(Math.abs(a.x() - b.x()) >= 18 || Math.abs(a.y() - b.y()) >= 18,
                        "Overlapping recipe slots: " + a.name() + "/" + b.name());
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
