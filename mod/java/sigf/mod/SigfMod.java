package sigf.mod;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** GTA V Takeover: a Los Santos parody city with wanted stars, police, traffic, a pistol, WASTED screens and a story. */
public final class SigfMod implements ModInitializer {
	public static Item PISTOL, GOLD_PISTOL, CASH;
	public static SoundEvent GUNSHOT, SIREN, CASH_SND, BOOM, ENGINE, CITY;

	static int wanted;          // 0..5 stars
	static long lastCrime;      // server tick of the last crime
	static long now;            // server tick counter
	static long lastShot;
	static int cash;
	static final String[] STREET = {"Los Santos Local", "Vinewood Tourist", "Grove Street Guy", "Beach Jogger", "Angry Pedestrian", "Franklin Fan", "Sunday Driver"};

	@Override
	public void onInitialize() {
		PISTOL = Sigf.item("pistol", p -> new Item(p.stacksTo(1)));
		GOLD_PISTOL = Sigf.item("golden_pistol", p -> new Item(p.stacksTo(1)));
		CASH = Sigf.item("cash", p -> new Item(p.stacksTo(64)));
		GUNSHOT = Sigf.registerSound("gunshot");
		SIREN = Sigf.registerSound("siren");
		CASH_SND = Sigf.registerSound("cashsnd");
		BOOM = Sigf.registerSound("boom");
		ENGINE = Sigf.registerSound("engine");
		CITY = Sigf.registerSound("city");

		ServerLifecycleEvents.SERVER_STARTED.register(srv -> City.build(srv.overworld()));
		ServerTickEvents.END_SERVER_TICK.register(s -> { now++; Car.tickAll(); Missions.tick(); });

		// The street stays busy: pedestrians and traffic refill.
		Sigf.every(5, () -> {
			if (!City.built || Sigf.host() == null) return;
			if (Sigf.near(Sigf.host().position(), 80, e -> e instanceof Villager).size() < 14) spawnPed(City.sidewalk());
			if (Car.count(false) < 7 && Missions.mission != 3) Car.civilian(Math.random() < 0.5, (int) (Math.random() * 5), Math.random() < 0.5 ? 1 : -1, (Math.random() < 0.5 ? City.x0 : City.z0) + 10 + Math.random() * 80);
		});
		Sigf.every(0.5, SigfMod::hud);
		Sigf.every(20, () -> { if (City.built && Sigf.host() != null) Sigf.sound(CITY, Sigf.host().position(), 0.6f, 1f); });
		Sigf.every(3, SigfMod::policeTick);
		Sigf.every(2, () -> {
			if (wanted > 0 && now - lastCrime > 20 * 25) { wanted--; lastCrime = now - 20 * 10; }
		});
		Sigf.every(1, () -> {
			if (wanted > 0 && Sigf.host() != null) Sigf.sound(SIREN, Sigf.host().position().add(8, 0, 8), 0.4f, 1f);
		});
		Sigf.after(0.5, () -> { if (City.built && Sigf.host() != null) Sigf.teleport(Sigf.host(), City.center(), Vec3.ZERO); });
		Sigf.after(1.5, () -> {
			if (!City.built) return;
			for (int i = 0; i < 14; i++) spawnPed(City.sidewalk());
			for (int i = 0; i < 7; i++) Car.civilian(i % 2 == 0, i % 5, i % 3 == 0 ? 1 : -1, (i % 2 == 0 ? City.z0 : City.x0) + 12 + i * 11);
		});
		// Playing at home: a pistol in the hand, the first job starts after a few seconds.
		Sigf.after(3, () -> {
			ServerPlayer h = Sigf.host();
			if (!Sigf.isDemo() && h != null && !h.isSpectator() && !h.getInventory().contains(st -> st.is(PISTOL))) h.getInventory().add(new ItemStack(PISTOL));
		});
		Sigf.after(8, () -> { if (!Sigf.isDemo() && Sigf.host() != null && !Sigf.host().isSpectator()) Missions.begin(0); });

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && entity instanceof Villager) crime(1);
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack st = player.getItemInHand(hand);
			if (!level.isClientSide() && player instanceof ServerPlayer sp && (st.is(PISTOL) || st.is(GOLD_PISTOL))) {
				fire(sp, st.is(GOLD_PISTOL) ? 12f : 7f);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(Missions.TAG)) {
				if (entity instanceof Zombie) { drop(entity.position(), 2); wasted(entity); }
			} else if (entity instanceof Villager) {
				drop(entity.position(), 2);
				wasted(entity);
			} else if (entity instanceof Zombie z && z.getCustomName() != null && z.getCustomName().getString().startsWith("LSPD")) {
				drop(entity.position(), 4);
				crime(1);
				wasted(entity);
			}
		});

		demo();
	}

	// --- Wanted level and crimes ---

	static void crime(int stars) {
		wanted = Math.min(5, wanted + stars);
		lastCrime = now;
		if (Sigf.host() != null) Sigf.sound(SoundEvents.NOTE_BLOCK_BELL.value(), Sigf.host().position(), 1f, 0.6f);
	}

	static void hud() {
		String stars = "";
		for (int i = 0; i < 5; i++) stars += i < wanted ? "★" : "☆";
		Component c = Component.literal("$" + cash + "  ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(stars).withStyle(wanted > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY, ChatFormatting.BOLD));
		String obj = Missions.hud();
		if (!obj.isEmpty()) c = c.copy().append(Component.literal("   " + obj).withStyle(ChatFormatting.YELLOW));
		for (ServerPlayer p : Sigf.players()) p.connection.send(new ClientboundSetActionBarTextPacket(c));
	}

	static void bigTitle(Component t, String sub, int stay) {
		for (ServerPlayer p : Sigf.players()) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(2, stay, 8));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
			p.connection.send(new ClientboundSetTitleTextPacket(t));
		}
	}

	static void wasted(Entity e) {
		Sigf.particles(ParticleTypes.CRIMSON_SPORE, e.position().add(0, 1, 0), 20, 0.5);
		bigTitle(Component.literal("WASTED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), "", 30);
	}

	static void missionPassed(String sub, int pay) {
		wanted = 0;
		cash += pay;
		Sigf.sound(SoundEvents.PLAYER_LEVELUP, Sigf.host().position(), 1f, 1f);
		bigTitle(Component.literal("MISSION PASSED").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), sub + "   $" + pay, 50);
	}

	static void drop(Vec3 pos, int n) {
		cash += 25 * n;
		Sigf.sound(CASH_SND, pos, 1f, 1f);
		var item = Sigf.spawn(EntityTypes.ITEM, pos.add(0, 0.5, 0));
		if (item != null) {
			item.setItem(new ItemStack(CASH, n));
			item.setDeltaMovement(0, 0.35, 0);
		}
	}

	// --- Pedestrians and police ---

	static void spawnPed(Vec3 pos) {
		Villager v = Sigf.spawn(EntityTypes.VILLAGER, pos);
		if (v == null) return;
		v.setCustomName(Component.literal(STREET[(int) (Math.random() * STREET.length)]));
		v.setCustomNameVisible(true);
		v.setPersistenceRequired();
	}

	static ItemStack dyed(Item item, int rgb) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return s;
	}

	static Zombie spawnCop(Vec3 pos) {
		Zombie z = Sigf.spawn(EntityTypes.ZOMBIE, pos);
		if (z == null) return null;
		z.setBaby(false);
		z.setCustomName(Component.literal("LSPD Officer"));
		z.setCustomNameVisible(true);
		z.setPersistenceRequired();
		z.setGlowingTag(true);
		z.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0x2F6BFF));
		z.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x2F6BFF));
		z.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x10286E));
		z.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x111111));
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(14);
		z.setHealth(14);
		z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
		z.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
		z.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(60);
		Sigf.particles(ParticleTypes.CLOUD, pos.add(0, 1, 0), 12, 0.4);
		return z;
	}

	static void policeTick() {
		ServerPlayer h = Sigf.host();
		if (h == null || wanted == 0 || !City.built || Missions.mission == 3) return;
		List<Entity> cops = Sigf.near(h.position(), 50, e -> e instanceof Zombie z && z.getCustomName() != null && z.getCustomName().getString().startsWith("LSPD"));
		if (wanted >= 3 && Car.count(true) == 0 && Math.random() < 0.6) {
			Vec3 p = City.sidewalk();
			for (int j = 0; j < 8 && p.distanceTo(h.position()) < 25; j++) p = City.sidewalk();
			new Car(p, true);
		}
		int want = Math.min(8, wanted * 2);
		for (int i = cops.size(); i < want && i < cops.size() + 2; i++) {
			Vec3 p = City.sidewalk();
			for (int j = 0; j < 8 && p.distanceTo(h.position()) < 12; j++) p = City.sidewalk();
			Zombie z = spawnCop(p);
			if (z != null) z.setTarget(h);
		}
	}

	// --- The pistol ---

	static void fire(ServerPlayer p, float damage) {
		if (now - lastShot < 6) return;
		lastShot = now;
		var lv = Sigf.level();
		Vec3 eye = p.getEyePosition(), dir = p.getLookAngle();
		Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
		Vec3 gun = eye.add(dir.scale(1.6)).add(right.scale(0.45)).add(0, -0.3, 0);
		Sigf.sound(GUNSHOT, gun, 1f, 1f);
		Sigf.particles(ParticleTypes.ELECTRIC_SPARK, gun, 4, 0.05);
		Sigf.particles(ParticleTypes.SMALL_FLAME, gun, 2, 0.02);
		for (double d = 1; d < 45; d += 0.5) {
			Vec3 pt = eye.add(dir.scale(d));
			BlockPos bp = BlockPos.containing(pt);
			if (!lv.getBlockState(bp).getCollisionShape(lv, bp).isEmpty()) {
				Sigf.particles(ParticleTypes.CRIT, pt, 8, 0.1);
				Sigf.particles(ParticleTypes.SMOKE, pt, 2, 0.05);
				return;
			}
			if (d > 3 && (int) (d * 2) % 3 == 0) Sigf.particles(ParticleTypes.END_ROD, pt, 1, 0.0);
			for (Entity e : Sigf.near(pt, 4, x -> x instanceof LivingEntity && x != p)) {
				if (!e.getBoundingBox().inflate(0.25).contains(pt)) continue;
				LivingEntity l = (LivingEntity) e;
				l.hurtServer(lv, lv.damageSources().playerAttack(p), damage);
				if (l instanceof Ravager) {
					Sigf.sound(SoundEvents.ANVIL_LAND, pt, 0.5f, 1.8f);
					Sigf.particles(ParticleTypes.ELECTRIC_SPARK, pt, 14, 0.3);
				}
				Sigf.particles(ParticleTypes.CRIMSON_SPORE, pt, 10, 0.2);
				Sigf.particles(ParticleTypes.CRIT, pt, 10, 0.2);
				if (!(l instanceof Ravager) && l.getBbHeight() < 3) {
					l.setDeltaMovement(l.getDeltaMovement().add(dir.x * 0.5, 0.25, dir.z * 0.5));
					l.syncVelocity = true;
				}
				if (l instanceof Villager) crime(1);
				return;
			}
		}
	}

	// --- Demo ---

	/** The demo bot aims at the best target (mission enemy, police car, cop, pedestrian) and shoots. */
	static void botShoot() {
		ServerPlayer h = Sigf.host();
		Entity best = null;
		double bd = 1e9;
		for (Entity e : Sigf.near(h.position(), 34, x -> x instanceof Zombie || x instanceof Villager || x instanceof Ravager)) {
			if (e instanceof Ravager r && Car.of(r) != null && !Car.of(r).police) continue;
			double d = e.position().distanceTo(h.position());
			if (e == Missions.boss) d -= 120;
			else if (e.entityTags().contains(Missions.TAG)) d -= 60;
			else if (e instanceof Ravager) d -= 20;
			else if (e instanceof Zombie) d -= 10;
			else if (wanted > 0 || Missions.active()) d += 40;
			if (d < bd) { bd = d; best = e; }
		}
		if (best != null) {
			Sigf.lookAt(h, best.position().add(0, best.getBbHeight() * 0.6, 0));
			fire(h, 7f);
		}
	}

	static void demo() {
		Sigf.demo(0.2, () -> {
			ServerPlayer h = Sigf.host();
			Sigf.command("time set 12300");
			Sigf.command("weather clear");
			h.getInventory().add(new ItemStack(PISTOL));
			h.getInventory().setSelectedSlot(0);
			// aerial shot over the city for the title
			Vec3 c = City.center();
			h.setNoGravity(true);
			Sigf.teleport(h, c.add(-30, 38, -34), Vec3.ZERO);
			Sigf.lookAt(h, c.add(0, 4, 0));
			Sigf.title("GRAND THEFT MINECRAFT V", "Welcome to Los Santos", 3.5);
			for (int i = 0; i < 5; i++) spawnPed(c.add(i * 2 - 4, 0, 4 + (i % 2) * 3));
		});
		Sigf.demo(3.0, () -> Sigf.lookAt(Sigf.host(), City.center().add(0, 6, 0)));
		Sigf.demo(4.2, () -> {
			ServerPlayer h = Sigf.host();
			h.setNoGravity(false);
			Vec3 c = City.center();
			Sigf.teleport(h, new Vec3(City.roadCentre(2, true), c.y, City.roadCentre(2, false) - 9), Vec3.ZERO);
			Sigf.lookAt(h, c.add(0.5, 1.6, 20));
		});
		for (double t = 5; t < 90; t += 0.35) Sigf.demo(t, SigfMod::botShoot);
		Sigf.demo(9, () -> Missions.begin(0));
	}
}
