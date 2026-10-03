package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/**
 * A car: a 3D model built from block displays that follows an invisible, silent Ravager "engine"
 * (it gives the car collision, health, hit reactions). Police cruisers chase the player; civilian cars
 * drive the city grid and run pedestrians over.
 */
final class Car {
	static final List<Car> ALL = new ArrayList<>();
	static int serial;
	static final String[] COLORS = {"red_concrete", "yellow_concrete", "blue_concrete", "white_concrete", "lime_concrete", "magenta_concrete", "orange_concrete", "cyan_concrete"};

	final boolean police;
	final Ravager engine;
	final List<Entity> parts = new ArrayList<>();
	final String tag;
	int ticks;
	boolean dead;
	// civilian driving: on road kr (0..4), along z (alongZ) or x, heading sign, at coordinate s
	boolean alongZ;
	int kr, sign, lastK = -1;
	double s;
	double speed = 0.42;

	static Object[][] parts(boolean police, String c) {
		String lower = police ? "black_concrete" : c;
		String doors = police ? "white_concrete" : c;
		String roof = police ? "white_concrete" : c;
		Object[][] p = {
			{lower, -0.9, 0.30, -2.0, 1.8, 0.55, 4.0},
			{doors, -0.92, 0.55, -1.1, 1.84, 0.40, 2.3},
			{police ? "gray_concrete" : c, -0.8, 0.85, 0.9, 1.6, 0.10, 1.0},
			{"light_blue_stained_glass", -0.78, 0.95, -1.2, 1.56, 0.55, 1.9},
			{roof, -0.82, 1.50, -1.2, 1.64, 0.10, 1.9},
			{police ? "black_concrete" : c, -0.78, 0.90, -2.0, 1.56, 0.10, 0.8},
			{"black_concrete", -1.00, 0.0, -1.55, 0.25, 0.55, 0.55},
			{"black_concrete", 0.75, 0.0, -1.55, 0.25, 0.55, 0.55},
			{"black_concrete", -1.00, 0.0, 0.95, 0.25, 0.55, 0.55},
			{"black_concrete", 0.75, 0.0, 0.95, 0.25, 0.55, 0.55},
			{"sea_lantern", -0.80, 0.50, 1.98, 0.35, 0.18, 0.06},
			{"sea_lantern", 0.45, 0.50, 1.98, 0.35, 0.18, 0.06},
			{"red_concrete", -0.80, 0.55, -2.03, 0.35, 0.15, 0.06},
			{"red_concrete", 0.45, 0.55, -2.03, 0.35, 0.15, 0.06},
			{"red_concrete", -0.70, 1.60, -0.55, 0.65, 0.16, 0.35},   // 14: light bar (red side)
			{"blue_concrete", 0.05, 1.60, -0.55, 0.65, 0.16, 0.35},   // 15: light bar (blue side)
		};
		if (!police) {
			p[14] = new Object[] {c.startsWith("yellow") ? "sea_lantern" : c, -0.3, 1.60, -0.45, 0.6, 0.14, 0.3};   // taxi sign
			p[15] = new Object[] {c, -0.01, 0.0, 0.0, 0.01, 0.01, 0.01};
		}
		return p;
	}

	Car(Vec3 pos, boolean police) {
		this.police = police;
		tag = "gtacar" + (serial++);
		String color = COLORS[(int) (Math.random() * COLORS.length)];
		Object[][] def = parts(police, color);
		for (int i = 0; i < def.length; i++) {
			Object[] p = def[i];
			Sigf.command(String.format(Locale.ROOT,
				"summon block_display %f %f %f {Tags:[\"%s\",\"%s_%d\"],block_state:\"minecraft:%s\",teleport_duration:2,"
					+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%ff,%ff,%ff],scale:[%ff,%ff,%ff]}}",
				pos.x, pos.y, pos.z, tag, tag, i, p[0], p[1], p[2], p[3], p[4], p[5], p[6]));
		}
		List<Entity> found = Sigf.near(pos, 3, x -> x.entityTags().contains(tag));
		for (int i = 0; i < def.length; i++) {
			String want = tag + "_" + i;
			for (Entity e : found) if (e.entityTags().contains(want)) { parts.add(e); break; }
		}
		engine = Sigf.spawn(EntityTypes.RAVAGER, pos);
		engine.setInvisible(true);
		engine.setSilent(true);
		engine.setPersistenceRequired();
		engine.getAttribute(Attributes.MAX_HEALTH).setBaseValue(police ? 40 : 30);
		engine.setHealth(police ? 40 : 30);
		engine.setCustomName(Component.literal(police ? "LSPD Cruiser" : "Civilian Car"));
		engine.entityTags().add(tag);
		if (police) {
			engine.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.45);
			engine.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(60);
		} else {
			engine.setNoAi(true);
			engine.setNoGravity(true);
		}
		Sigf.particles(ParticleTypes.CLOUD, pos.add(0, 1, 0), 25, 0.8);
		ALL.add(this);
	}

	/** A civilian car on road kr of the grid, heading along z or x, at coordinate s. */
	static Car civilian(boolean alongZ, int kr, int sign, double s) {
		Car c = new Car(City.onFloor(City.roadCentre(kr, alongZ), s), false);
		c.alongZ = alongZ; c.kr = kr; c.sign = sign; c.s = s;
		c.place();
		return c;
	}

	void place() {
		double x, z;
		if (alongZ) { x = City.roadCentre(kr, true) + (sign > 0 ? -1.5 : 1.5); z = s; }
		else { z = City.roadCentre(kr, false) + (sign > 0 ? 1.5 : -1.5); x = s; }
		float yaw = alongZ ? (sign > 0 ? 0f : 180f) : (sign > 0 ? -90f : 90f);
		engine.snapTo(x, City.floor + 1, z, yaw, 0f);
		engine.yBodyRot = yaw;
		engine.yHeadRot = yaw;
	}

	void drive() {
		s += sign * speed;
		for (int k = 0; k < 5; k++) {
			double c = City.roadCentre(k, !alongZ);   // centre line of the cross road k, on our axis
			if (Math.abs(s - c) < speed * 0.6 && k != lastK) {
				lastK = k;
				boolean mustTurn = (k == 4 && sign > 0) || (k == 0 && sign < 0);
				if (mustTurn || Math.random() < 0.3) {
					double cx = City.roadCentre(kr, alongZ);
					int ns = Math.random() < 0.5 ? 1 : -1;
					if (kr == 0) ns = 1;
					if (kr == 4) ns = -1;
					alongZ = !alongZ; kr = k; sign = ns; s = cx;
					lastK = -1;
				}
				break;
			}
		}
		if (lastK >= 0 && Math.abs(s - City.roadCentre(lastK, !alongZ)) > speed * 2) lastK = -1;
		place();
	}

	static Car of(Entity e) {
		for (Car c : ALL) if (c.engine == e) return c;
		return null;
	}

	void tick() {
		ticks++;
		if (dead) return;
		if (!engine.isAlive()) { explode(); return; }
		engine.setInvisible(true);
		Vec3 p = engine.position();
		float yaw = police ? engine.yBodyRot : engine.getYRot();
		for (Entity e : parts) e.snapTo(p.x, p.y, p.z, yaw, 0f);
		ServerPlayer h = Sigf.host();
		if (police) {
			if (h != null) {
				if (engine.getTarget() == null) engine.setTarget(h);
				double d = p.distanceTo(h.position());
				if (d > 7 && ticks % 10 == 0) engine.getNavigation().moveTo(h.getX(), h.getY(), h.getZ(), 1.8);
				else if (d < 5.5) engine.getNavigation().stop();
			}
			if (ticks % 5 == 0 && parts.size() >= 16) {
				boolean a = (ticks / 5) % 2 == 0;
				set(parts.get(14), a ? "red_concrete" : "blue_concrete");
				set(parts.get(15), a ? "blue_concrete" : "red_concrete");
				Sigf.particles(a ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME, p.add(0, 2.0, 0), 1, 0.15);
			}
			if (ticks % 20 == 0) Sigf.sound(SigfMod.SIREN, p, 1.2f, 1f);
		} else {
			drive();
			if (ticks % 25 == 0 && h != null && p.distanceTo(h.position()) < 30) Sigf.sound(SigfMod.ENGINE, p, 0.5f, 0.9f + (float) Math.random() * 0.3f);
		}
		// damage feedback: smoke, then fire, grows as it gets shot up
		float hp = engine.getHealth() / engine.getMaxHealth();
		Vec3 bonnet = p.add(0, 1.0, 0).add(Vec3.directionFromRotation(0, yaw).scale(1.4));
		if (hp < 0.6f && ticks % 4 == 0) Sigf.particles(ParticleTypes.SMOKE, bonnet, 2, 0.12);
		if (hp < 0.3f && ticks % 4 == 0) Sigf.particles(ParticleTypes.FLAME, bonnet, 2, 0.15);
		// run over whoever is in front
		for (Entity e : Sigf.near(p.add(Vec3.directionFromRotation(0, yaw).scale(1.2)), 1.8, x -> x instanceof LivingEntity && x != engine && !(x instanceof ServerPlayer) && !(x instanceof Ravager))) {
			if (!police && !(e instanceof net.minecraft.world.entity.npc.villager.Villager)) continue;
			((LivingEntity) e).hurtServer(Sigf.level(), Sigf.level().damageSources().mobAttack(engine), police ? 2f : 6f);
			e.setDeltaMovement(Vec3.directionFromRotation(0, yaw).scale(0.9).add(0, 0.45, 0));
			e.syncVelocity = true;
			Sigf.sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, e.position(), 1f, 0.8f);
		}
	}

	static void set(Entity part, String block) {
		Sigf.command("data merge entity " + part.getUUID() + " {block_state:\"minecraft:" + block + "\"}");
	}

	void explode() {
		dead = true;
		Vec3 p = engine.position();
		for (Entity e : parts) e.discard();
		engine.discard();
		Sigf.particles(ParticleTypes.EXPLOSION_EMITTER, p.add(0, 1, 0), 1, 0.3);
		Sigf.particles(ParticleTypes.FLAME, p.add(0, 1, 0), 50, 1.0);
		Sigf.particles(ParticleTypes.LAVA, p.add(0, 1, 0), 20, 0.8);
		Sigf.particles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.add(0, 1.5, 0), 8, 0.8);
		Sigf.sound(SigfMod.BOOM, p, 2.5f, 1f);
		for (Entity e : Sigf.near(p, 5, x -> x instanceof LivingEntity && x != engine && !(x instanceof ServerPlayer))) {
			((LivingEntity) e).hurtServer(Sigf.level(), Sigf.level().damageSources().explosion(null, null), 10f);
			e.setDeltaMovement(e.position().subtract(p).normalize().scale(0.9).add(0, 0.6, 0));
			e.syncVelocity = true;
		}
		SigfMod.drop(p, police ? 8 : 3);
		if (!engine.entityTags().contains(Missions.TAG)) SigfMod.crime(police ? 2 : 1);
		ALL.remove(this);
	}

	void remove() {
		dead = true;
		for (Entity e : parts) e.discard();
		engine.discard();
		ALL.remove(this);
	}

	static void tickAll() {
		for (Car c : new ArrayList<>(ALL)) {
			try { c.tick(); } catch (RuntimeException e) { Sigf.log("car: " + e); }
		}
	}

	static int count(boolean police) {
		int n = 0;
		for (Car c : ALL) if (c.police == police) n++;
		return n;
	}
}
