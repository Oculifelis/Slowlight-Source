package dev.slowlight.autotest;

import dev.slowlight.compat.AgeingRecipeCategory;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of the play-test: makes a fresh superflat world, waits for {@link ServerChecks} to set the scene,
 * photographs the sealed kiln with the Jade overlay, the row by day and the JEI Patient Kiln page, then asks for
 * the seven-day sprint and photographs the lantern at night. Stops the client at the end.
 */
@EventBusSubscriber(modid = "slowlight_autotest", value = Dist.CLIENT)
public final class ClientDriver {
    private static final String WORLD = "sltest";
    private static final long TIMEOUT_NANOS = 8L * 60L * 1_000_000_000L;

    private static boolean creating;
    private static long startedAt;
    private static int idle;
    private static int step;
    private static int wait;
    private static boolean finished;

    private ClientDriver() {
    }

    @SubscribeEvent
    static void sound(PlaySoundEvent event) {
        if (ServerChecks.ON && event.getSound() != null && event.getSound().getLocation().getNamespace().equals("slowlight")) {
            ServerChecks.info("client.sound", "client asked to play " + event.getSound().getLocation() + " (" + event.getSound().getSource() + ")");
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ServerChecks.ON || finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (startedAt != 0 && System.nanoTime() - startedAt > TIMEOUT_NANOS) {
            ServerChecks.fail("timeout", "client driver ran out of time at step " + step + ", server phase " + ServerChecks.phase);
            stop(mc);
            return;
        }
        try {
            drive(mc);
        } catch (Throwable t) {
            ServerChecks.LOG.error("SLTEST FAIL exception :: client step threw", t);
            stop(mc);
        }
    }

    private static void drive(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && !(mc.screen instanceof TitleScreen) && mc.getOverlay() == null && ++idle > 200) {
                mc.setScreen(new TitleScreen());
                idle = 0;
            }
            if (!creating && mc.screen instanceof TitleScreen title && mc.getOverlay() == null) {
                creating = true;
                startedAt = System.nanoTime();
                ServerChecks.info("client", "title screen reached, window visible="
                        + GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_VISIBLE)
                        + ", focused=" + GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED));
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.renderDistance().set(5);
                mc.options.simulationDistance().set(5);
                LevelStorageSource source = mc.getLevelSource();
                if (source.levelExists(WORLD)) {
                    try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                        access.deleteLevel();
                    } catch (Exception e) {
                        ServerChecks.LOG.warn("SLTEST could not delete old world", e);
                    }
                }
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261102L, false, false),
                        ra -> ra.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), title);
            }
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case 0 -> {
                if (ServerChecks.phase == ServerChecks.SCENE_READY) {
                    mc.options.hideGui = false;
                    soundCheck(mc);
                    trimCheck(mc);
                    aerialCheck(mc);
                    step = 1;
                    wait = 80;
                }
            }
            case 1 -> {
                shot(mc, "1_kiln_sealed_jade");
                ServerChecks.clientWantsWideView = true;
                step = 2;
                wait = 60;
            }
            case 2 -> {
                mc.options.hideGui = true;
                step = 21;
                wait = 5;
            }
            case 21 -> {
                shot(mc, "2_row_by_day");
                step = 3;
                wait = 5;
            }
            case 3 -> {
                mc.options.hideGui = false;
                if (AutotestJeiPlugin.runtime == null) {
                    ServerChecks.fail("jei.runtime", "JEI runtime never became available");
                    step = 5;
                } else {
                    AutotestJeiPlugin.runtime.getRecipesGui().showTypes(List.of(AgeingRecipeCategory.TYPE));
                    step = 4;
                    wait = 60;
                }
            }
            case 4 -> {
                ServerChecks.info("jei.screen", "screen open: " + (mc.screen == null ? null : mc.screen.getClass().getName()));
                shot(mc, "3_jei_patient_kiln");
                step = 41;
                wait = 5;
            }
            case 41 -> {
                mc.setScreen(null);
                step = 5;
                wait = 20;
            }
            case 5 -> {
                ServerChecks.clientWantsSprint = true;
                step = 6;
            }
            case 6 -> {
                if (ServerChecks.phase == ServerChecks.NIGHT_READY) {
                    mc.options.hideGui = true;
                    step = 7;
                    wait = 80;
                }
            }
            case 7 -> {
                shot(mc, "4_lantern_at_night");
                ServerChecks.clientWantsKilnView = true;
                step = 71;
                wait = 5;
            }
            case 71 -> {
                if (ServerChecks.phase == ServerChecks.KILNS_READY) {
                    step = 72;
                    wait = 80;
                }
            }
            case 72 -> {
                shot(mc, "5_finished_in_kilns");
                ServerChecks.clientTookKilnShot = true;
                step = 73;
                wait = 5;
            }
            case 73 -> {
                if (ServerChecks.phase == ServerChecks.KILNS_CHECKED) {
                    ServerChecks.clientWantsLongView = true;
                    step = 74;
                }
            }
            case 74 -> {
                if (ServerChecks.phase == ServerChecks.LONG_READY) {
                    step = 75;
                    wait = 80;
                }
            }
            case 75 -> {
                shot(mc, "6_long_kilns_done");
                ServerChecks.clientTookLongShot = true;
                step = 76;
                wait = 5;
            }
            case 76 -> {
                if (ServerChecks.phase == ServerChecks.LONG_CHECKED) {
                    step = 77;
                    wait = 20;
                }
            }
            case 77 -> {
                // The guide book, opened the way Patchouli's API does it, if Patchouli is there.
                try {
                    Class<?> api = Class.forName("vazkii.patchouli.api.PatchouliAPI");
                    Object inst = api.getMethod("get").invoke(null);
                    inst.getClass().getMethod("openBookGUI", ResourceLocation.class)
                            .invoke(inst, ResourceLocation.fromNamespaceAndPath("slowlight", "guide"));
                    step = 78;
                    wait = 40;
                } catch (ReflectiveOperationException | LinkageError e) {
                    ServerChecks.info("guide", "Patchouli not available: " + e);
                    step = 8;
                }
            }
            case 78 -> {
                ServerChecks.check("client.guide_book_opens", mc.screen != null && mc.screen.getClass().getName().contains("patchouli"),
                        "screen after opening the guide: " + (mc.screen == null ? null : mc.screen.getClass().getName()));
                shot(mc, "7_guide_book");
                step = 79;
                wait = 5;
            }
            case 79 -> {
                mc.setScreen(null);
                step = 8;
                wait = 10;
            }
            case 8 -> {
                mc.setScreen(new Showcase());
                step = 81;
                wait = 30;
            }
            case 81 -> {
                shot(mc, "8_crafts_items");
                mc.setScreen(null);
                CraftsChecks.clientWantsView = true;
                step = 82;
                wait = 5;
            }
            case 82 -> {
                if (CraftsChecks.viewReady) {
                    step = 83;
                    wait = 80;
                }
            }
            case 83 -> {
                shot(mc, "9_crafts_world");
                CraftsChecks.clientWantsGround = true;
                step = 84;
                wait = 5;
            }
            case 84 -> {
                if (CraftsChecks.groundReady) {
                    step = 85;
                    wait = 60;
                }
            }
            case 85 -> {
                shot(mc, "10_pressed_ground");
                for (var f : List.of(net.minecraft.world.item.Items.POPPY, net.minecraft.world.item.Items.CORNFLOWER,
                        net.minecraft.world.item.Items.OXEYE_DAISY, net.minecraft.world.item.Items.DANDELION,
                        net.minecraft.world.item.Items.ALLIUM, net.minecraft.world.item.Items.BLUE_ORCHID,
                        net.minecraft.world.item.Items.LILY_OF_THE_VALLEY, net.minecraft.world.item.Items.TORCHFLOWER,
                        net.minecraft.world.item.Items.RED_TULIP, net.minecraft.world.item.Items.AZURE_BLUET,
                        net.minecraft.world.item.Items.GLOW_LICHEN)) {
                    var l = dev.slowlight.client.FlowerColours.of(f);
                    ServerChecks.info("pressed.look", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(f) + " petals #"
                            + Integer.toHexString(l.petals()) + " centre #" + Integer.toHexString(l.centre()) + " bloom " + l.bloom()
                            + (l.twoTone() ? " two-tone" : "") + " glow " + l.glow() + " look "
                            + dev.slowlight.client.PressedLooks.of(f, l));
                }
                step = 9;
                wait = 5;
            }
            default -> {
                ServerChecks.info("client", "done");
                ServerChecks.phase = ServerChecks.DONE;
                stop(mc);
            }
        }
    }

    /** The new items drawn big on a plain screen: pressed flowers (each its own flower on a card), the press, the
     * candle, and every step of weathered Gloamwood. */
    private static final class Showcase extends net.minecraft.client.gui.screens.Screen {
        Showcase() {
            super(net.minecraft.network.chat.Component.literal("crafts"));
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, 0xFF30343C);
            List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
            for (var f : List.of(net.minecraft.world.item.Items.POPPY, net.minecraft.world.item.Items.CORNFLOWER,
                    net.minecraft.world.item.Items.OXEYE_DAISY, net.minecraft.world.item.Items.TORCHFLOWER,
                    net.minecraft.world.item.Items.CHERRY_LEAVES, net.minecraft.world.item.Items.PEONY)) {
                stacks.add(dev.slowlight.SlowlightCrafts.pressed(f.builtInRegistryHolder()));
            }
            stacks.add(dev.slowlight.SlowlightCrafts.FLOWER_PRESS_ITEM.get().getDefaultInstance());
            stacks.add(dev.slowlight.SlowlightCrafts.SLOW_CANDLE_ITEM.get().getDefaultInstance());
            for (String id : List.of("gloamwood_planks", "faded_gloamwood_planks", "weathered_gloamwood_planks",
                    "silvered_gloamwood_planks", "waxed_silvered_gloamwood_planks", "silvered_gloamwood_stairs")) {
                stacks.add(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(ResourceLocation.fromNamespaceAndPath("slowlight", id))));
            }
            float scale = 3.5F;
            int cols = 6;
            for (int i = 0; i < stacks.size(); i++) {
                g.pose().pushPose();
                g.pose().translate(20 + (i % cols) * 18 * scale, 20 + (i / cols) * 18 * scale, 0);
                g.pose().scale(scale, scale, 1);
                g.renderItem(stacks.get(i), 0, 0);
                g.pose().popPose();
            }
        }
    }

    private static void soundCheck(Minecraft mc) {
        for (String s : List.of("block.patient_kiln.seal", "block.patient_kiln.open")) {
            ResourceLocation ev = ResourceLocation.fromNamespaceAndPath("slowlight", s);
            ServerChecks.check("client.sounds_json." + s, mc.getSoundManager().getSoundEvent(ev) != null, ev + " known to the sound manager");
        }
        for (String s : List.of("patient_light", "a_week_in", "lantern_hours")) {
            ResourceLocation ev = ResourceLocation.fromNamespaceAndPath("slowlight", "music_disc." + s);
            ResourceLocation ogg = ResourceLocation.fromNamespaceAndPath("slowlight", "sounds/records/" + s + ".ogg");
            boolean known = mc.getSoundManager().getSoundEvent(ev) != null;
            boolean file = mc.getResourceManager().getResource(ogg).isPresent();
            ServerChecks.check("client.sounds_json." + s, known && file, ev + " known to the sound manager=" + known + ", " + ogg + " present=" + file);
        }
    }

    /** The trim's sprites made it into both atlases, and a trimmed chestplate's icon draws the shard-coloured trim. */
    private static void trimCheck(Minecraft mc) {
        var trims = mc.getModelManager().getAtlas(net.minecraft.client.renderer.Sheets.ARMOR_TRIMS_SHEET);
        var blocks = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        ResourceLocation missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        for (String s : List.of("slowlight:trims/models/armor/patience_slowlight", "slowlight:trims/models/armor/patience_leggings_slowlight",
                "slowlight:trims/models/armor/patience_iron", "slowlight:trims/models/armor/patience_iron_darker",
                "minecraft:trims/models/armor/coast_slowlight", "minecraft:trims/models/armor/bolt_leggings_slowlight")) {
            ResourceLocation id = ResourceLocation.parse(s);
            ServerChecks.check("client.trim_sprite." + id.getPath().substring(id.getPath().lastIndexOf('/') + 1),
                    !trims.getSprite(id).contents().name().equals(missing), s + " in the armour trim atlas");
        }
        ResourceLocation icon = ResourceLocation.parse("minecraft:trims/items/chestplate_trim_slowlight");
        ServerChecks.check("client.trim_icon_sprite", !blocks.getSprite(icon).contents().name().equals(missing), icon + " in the blocks atlas");
        if (ServerChecks.trimmed != null) {
            net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
            chest.set(net.minecraft.core.component.DataComponents.TRIM, ServerChecks.trimmed);
            var model = mc.getItemRenderer().getModel(chest, mc.level, null, 0);
            boolean drawsTrim = model.getQuads(null, null, net.minecraft.util.RandomSource.create()).stream()
                    .anyMatch(q -> q.getSprite().contents().name().equals(icon));
            ServerChecks.check("client.trimmed_chestplate_icon", drawsTrim, "iron chestplate with the shard trim draws " + icon);
        }
    }

    /** With Aerial Hell loaded, each recoloured food's icon draws the sprite the atlas made from Aerial Hell's own texture
     *  and our palette; the sprite is saved to run/aerial/ so the test script can compare it to the approved icon. */
    private static void aerialCheck(Minecraft mc) {
        if (!net.neoforged.fml.ModList.get().isLoaded("aerialhell")) return;
        for (String[] f : new String[][] {{"candied_glowing_stick_fruit", "glowing_stick_fruit"}, {"stellar_rock_sugar", "stellar_stone_crystal"},
                {"cured_phantom_meat", "cooked_phantom_meat"}, {"cured_turtle_meat", "cooked_turtle_meat"}, {"stellar_rusk", "stellar_bread"}}) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("slowlight:" + f[0]));
            var model = mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item), mc.level, null, 0);
            var sprite = model.getParticleIcon().contents();
            ResourceLocation want = ResourceLocation.parse("aerialhell:item/" + f[1] + "_slowlight");
            ServerChecks.check("client.aerial_sprite." + f[0], sprite.name().equals(want), f[0] + " draws " + sprite.name() + ", wanted " + want);
            try {
                java.nio.file.Path out = mc.gameDirectory.toPath().resolve("aerial").resolve(f[0] + ".png");
                java.nio.file.Files.createDirectories(out.getParent());
                sprite.getOriginalImage().writeToFile(out);
            } catch (java.io.IOException e) {
                ServerChecks.check("client.aerial_dump." + f[0], false, e.toString());
            }
        }
    }

    private static void shot(Minecraft mc, String name) {
        String file = "sltest_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> { });
        ServerChecks.info("shot", file);
    }

    private static void stop(Minecraft mc) {
        finished = true;
        ServerChecks.LOG.info("SLTEST done");
        mc.stop();
    }
}
