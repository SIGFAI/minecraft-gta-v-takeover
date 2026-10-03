package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/**
 * The story: four short jobs for Dusty Dale the scarecrow getaway driver, ending with the boss "Big Smoky".
 * One mission at a time, one stage at a time; checks are distance, kills, timers.
 */
final class Missions {
	static final String TAG = "gta_mission";
	static int mission = -1;            // -1 not started, 0..3 active, 4 finished
	static int stage;
	static long stageAt;
	static Vec3 marker;
	static String line = "";
	static int survive;                 // seconds left in the survive stage
	static boolean boss2;
	static Zombie boss;
	static Car target;
	static ServerBossEvent bar;
	static final String[] NAMES = {"Cheesecake, Please", "Palms Away", "Grand Theft Pudding", "Big Smoky's Last Donut"};
	static final String[] BRIEF = {
		"Gladys says the recipe is in the bank. Knock politely. With a pistol.",
		"Big Smoky's goons are chasing you. Lose them in the palms!",
		"Smoky's armored cruiser carries the secret sauce. Blow it up, pal.",
		"Big Smoky guards the final donut. Bring a bib."};
	static final String[] DONE = {"Respect +1 (nobody asked)", "Palm Tree Whisperer", "Boom Goes The Pudding", "Cheesecake Justice"};
	static final int[] PAY = {5, 8, 12, 50};

	static Vec3 bankDoor() { return City.onFloor(City.x0 + 39.0, City.z0 + 30.5); }
	static Vec3 palms() { return City.onFloor(City.x0 + 63.5, City.z0 + 63.5); }

	static boolean active() { return mission >= 0 && mission < 4; }

	static void begin(int n) {
		cleanup();
		mission = n;
		stage = 0;
		stageAt = SigfMod.now;
		boss2 = false;
		Sigf.title("MISSION " + (n + 1), NAMES[n], Sigf.isDemo() ? 2.2 : 4);
		Sigf.say("Gladys: " + BRIEF[n]);
		Sigf.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, Sigf.host().position(), 1f, 1f);
		switch (n) {
			case 0 -> { marker = bankDoor(); line = "Reach the bank"; }
			case 1 -> { marker = palms(); line = "Get to the palm park"; }
			case 2 -> {
				marker = City.onFloor(City.x0 + 75, City.z0 + 85);
				line = "Find the Smoky Cruiser";
				target = new Car(marker, true);
				float hpv = Sigf.isDemo() ? 70 : 90;
				target.engine.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hpv);
				target.engine.setHealth(hpv);
				target.engine.entityTags().add(TAG);
				target.engine.setCustomName(Component.literal("Smoky Cruiser"));
				target.engine.setCustomNameVisible(true);
			}
			default -> {
				marker = City.center();
				line = "Go to the crossroads";
				for (Car c : new ArrayList<>(Car.ALL)) if (!c.police && c.engine.position().distanceTo(marker) < 30) c.remove();
			}
		}
		if (Sigf.isDemo()) Sigf.after(mission == 2 ? 0.5 : 1.0, () -> assistTravel());
	}

	/** In the demo the bot "drives" to the marker. */
	static void assistTravel() {
		if (!active() || stage != 0 || marker == null) return;
		ServerPlayer h = Sigf.host();
		Vec3 to, look;
		switch (mission) {
			case 0 -> { to = marker.subtract(marker.subtract(h.position()).normalize().scale(3)); look = marker; }
			case 1 -> { to = City.onFloor(City.x0 + 62.9, City.z0 + 60.5); look = marker; }
			case 2 -> { to = City.onFloor(City.x0 + 75, City.z0 + 64); look = marker; }
			default -> { to = City.center().add(-5, 0, 0); look = City.center().add(12, 0, 0); }
		}
		Sigf.teleport(h, to, Vec3.ZERO);
		Sigf.lookAt(h, look.add(0, 1.5, 0));
	}

	static void cleanup() {
		if (target != null && !target.dead) target.remove();
		for (Entity e : tagged()) if (e instanceof Zombie) e.discard();
		if (bar != null) { bar.removeAllPlayers(); bar = null; }
		boss = null;
		target = null;
	}

	static Zombie guard(Vec3 pos, String name, int rgb, boolean helmet) {
		Zombie z = Sigf.spawn(EntityTypes.ZOMBIE, pos);
		z.setBaby(false);
		z.setCustomName(Component.literal(name));
		z.setCustomNameVisible(true);
		z.setPersistenceRequired();
		z.entityTags().add(TAG);
		z.setItemSlot(EquipmentSlot.HEAD, SigfMod.dyed(Items.LEATHER_HELMET, 0x111111));
		z.setItemSlot(EquipmentSlot.CHEST, SigfMod.dyed(Items.LEATHER_CHESTPLATE, rgb));
		z.setItemSlot(EquipmentSlot.LEGS, SigfMod.dyed(Items.LEATHER_LEGGINGS, 0x222222));
		z.setItemSlot(EquipmentSlot.FEET, SigfMod.dyed(Items.LEATHER_BOOTS, 0x111111));
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(16);
		z.setHealth(16);
		z.setTarget(Sigf.host());
		Sigf.particles(ParticleTypes.CLOUD, pos.add(0, 1, 0), 10, 0.4);
		return z;
	}

	static List<Entity> tagged() {
		return Sigf.near(City.center(), 160, e -> e.entityTags().contains(TAG));
	}

	static int alive() {
		int n = 0;
		for (Entity e : tagged()) if (e instanceof Zombie) n++;
		return n;
	}

	static void pass() {
		int n = mission;
		Vec3 p = Sigf.host().position();
		SigfMod.drop(p.add(0, 0, 0), PAY[n] * 1);
		SigfMod.missionPassed(DONE[n], PAY[n] * 100);
		SigfMod.wanted = 0;
		cleanup();
		if (n == 3) { finish(); return; }
		final int next = n + 1;
		mission = -1;
		Sigf.after(Sigf.isDemo() ? 2.0 : 6, () -> begin(next));
	}

	static void finish() {
		mission = 4;
		line = "";
		ServerPlayer h = Sigf.host();
		h.getInventory().add(new ItemStack(SigfMod.GOLD_PISTOL));
		for (int i = 0; i < 9; i++) if (h.getInventory().getItem(i).is(SigfMod.GOLD_PISTOL)) h.getInventory().setSelectedSlot(i);
		Sigf.after(3.5, () -> Sigf.title("THE END", "Dusty Dale retired to a cheesecake bakery.", 6));
		Sigf.after(8, () -> Sigf.say("Los Santos: Palm Panic - a parody. Thanks for playing!"));
	}

	static void advance() { stage++; stageAt = SigfMod.now; }

	static void tick() {
		try { tick0(); } catch (RuntimeException e) { Sigf.log("missions: " + e); }
	}

	static void tick0() {
		if (!City.built) return;
		ServerPlayer h = Sigf.host();
		if (h == null) return;
		long t = SigfMod.now;
		long inStage = t - stageAt;
		// marker beam
		if (marker != null && active() && t % 4 == 0) {
			for (int y = 0; y < 14; y += 2) Sigf.particles(ParticleTypes.WAX_ON, marker.add(0, y, 0), 1, 0.25);
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + t * 0.05;
				Sigf.particles(ParticleTypes.END_ROD, marker.add(Math.cos(a) * 1.6, 0.2, Math.sin(a) * 1.6), 1, 0.0);
			}
		}
		if (!active()) return;
		if (t % 20 == 0) {
			for (Entity e : tagged()) if (e instanceof Zombie z) { z.setTarget(h); if (z.distanceTo(h) > 4) z.getNavigation().moveTo(h, 1.1); }
		}
		double dist = marker == null ? 0 : Math.hypot(h.getX() - marker.x, h.getZ() - marker.z);
		switch (mission) {
			case 0 -> {
				if (stage == 0) {
					line = "Reach the bank (" + (int) dist + " m)";
					if (dist < 4) {
						advance();
						for (int i = 0; i < 3; i++) guard(marker.add(i * 2 - 2, 0, -3 + (i == 1 ? 1 : 0)), "Bank Guard", 0x444444, true);
						Sigf.say("Bank Guard: Hey! This is a bank, not a cheesecake shop!");
					}
				} else {
					int n = alive();
					line = "Take out the bank guards (" + n + " left)";
					marker = null;
					if (inStage > 20 && n == 0) pass();
				}
			}
			case 1 -> {
				if (stage == 0) {
					line = "Get to the palm park (" + (int) dist + " m)";
					if (dist < 5) {
						advance();
						survive = Sigf.isDemo() ? 6 : 25;
						SigfMod.wanted = Math.max(SigfMod.wanted, 3);
						SigfMod.lastCrime = t;
						for (int i = 0; i < 3; i++) {
							Zombie z = SigfMod.spawnCop(City.onFloor(marker.x + 12 - i * 3, marker.z + 12));
							if (z != null) { z.entityTags().add(TAG); z.setTarget(h); }
						}
					}
				} else {
					SigfMod.lastCrime = t;
					if (t % 20 == 0) survive--;
					line = "Survive in the palms: " + Math.max(0, survive) + " s" + (dist > 14 ? "  - get back!" : "");
					if (t % 100 == 0 && alive() < 6) {
						Zombie z = SigfMod.spawnCop(City.onFloor(marker.x - 12, marker.z + 12));
						if (z != null) { z.entityTags().add(TAG); z.setTarget(h); }
					}
					if (survive <= 0) pass();
				}
			}
			case 2 -> {
				if (target == null || target.dead) {
					if (inStage > 40) pass();
					return;
				}
				marker = target.engine.position();
				line = "Destroy the Smoky Cruiser (" + (int) target.engine.getHealth() + " HP)";
				if (t % 60 == 0) target.engine.setTarget(h);
			}
			default -> {
				if (stage == 0) {
					line = "Go to the crossroads (" + (int) dist + " m)";
					if (dist < 6 || inStage > 20 * 12) {
						advance();
						spawnBoss();
					}
				} else if (boss != null) {
					float hp = boss.getHealth();
					line = "Defeat Big Smoky (" + (int) hp + " HP)";
					marker = null;
					if (bar != null) bar.setProgress(Math.max(0f, hp / boss.getMaxHealth()));
					if (!boss2 && hp < boss.getMaxHealth() * 0.5f) {
						boss2 = true;
						Sigf.title("BIG SMOKY IS ANGRY", "Reinforcements!", 2);
						boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
						for (int i = 0; i < 3; i++) {
							Zombie z = SigfMod.spawnCop(City.onFloor(boss.getX() + i * 3 - 3, boss.getZ() + 8));
							if (z != null) { z.entityTags().add(TAG); z.setTarget(h); }
						}
					}
					if (!boss.isAlive()) pass();
				}
			}
		}
	}

	static void spawnBoss() {
		Vec3 p = City.center().add(7, 0, 0);
		Zombie z = guard(p, "Big Smoky", 0x2F6BFF, true);
		z.getAttribute(Attributes.SCALE).setBaseValue(2.0);
		float hpv = Sigf.isDemo() ? 70 : 140;
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hpv);
		z.setHealth(hpv);
		z.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.26);
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
		z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_CARROT));
		z.setGlowingTag(true);
		boss = z;
		SigfMod.wanted = 4;
		SigfMod.lastCrime = SigfMod.now;
		bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("Big Smoky"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		for (ServerPlayer pl : Sigf.players()) bar.addPlayer(pl);
		Sigf.sound(SoundEvents.ENDER_DRAGON_GROWL, p, 1f, 0.8f);
		Sigf.say("Big Smoky: Nobody touches my donuts!");
	}

	static String hud() { return active() ? "MISSION " + (mission + 1) + ": " + line : (mission == 4 ? "All jobs done - free roam" : ""); }
}
