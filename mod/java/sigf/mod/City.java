package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * "Los Santos": an original little city of roads, towers, shops, parks with palm trees and street lamps,
 * built with blocks on top of the spawn area. 4 x 4 lots, roads 6 wide, pitch 24.
 */
final class City {
	static final int SIZE = 102, PITCH = 24, ROAD = 6, LOT = 18;
	static int x0, z0, floor;            // south-west corner (road tile 0,0) and the floor height
	static boolean built;
	static final List<int[]> LOTS = new ArrayList<>();   // lot origin x, z, kind (0 tower, 1 shop, 2 park, 3 bank)
	static final String[] WALLS = {"white_concrete", "light_gray_concrete", "orange_concrete", "cyan_concrete", "pink_concrete",
		"yellow_concrete", "light_blue_concrete", "purple_concrete", "red_concrete", "lime_concrete", "brown_concrete"};

	static final Block B_BLACK_CONCRETE = block("black_concrete");
	static final Block B_BLUE_CONCRETE = block("blue_concrete");
	static final Block B_GRAY_CONCRETE = block("gray_concrete");
	static final Block B_LIGHT_BLUE_STAINED_GLASS = block("light_blue_stained_glass");
	static final Block B_LIGHT_GRAY_CONCRETE = block("light_gray_concrete");
	static final Block B_RED_CONCRETE = block("red_concrete");
	static final Block B_RED_WOOL = block("red_wool");
	static final Block B_WHITE_CONCRETE = block("white_concrete");
	static final Block B_WHITE_WOOL = block("white_wool");
	static final Block B_YELLOW_CONCRETE = block("yellow_concrete");
	static ServerLevel lv;
	static final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

	static void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
	static void set(int x, int y, int z, BlockState s) {
		mp.set(x, y, z);
		lv.setBlock(mp, s, 2 | 16);
	}
	static Block block(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
	}

	/** World centre of the main crossroads, on the floor. */
	static Vec3 center() { return new Vec3(x0 + 50.5 + 0.0, floor + 1, z0 + 50.5); }
	/** The road lines (centre coordinates, world) in x then in z are at x0 + 3 + k * 24. */
	static double roadCentre(int k, boolean xAxis) { return (xAxis ? x0 : z0) + 3 + k * PITCH; }

	static void build(ServerLevel level) {
		lv = level;
		sigf.kit.Sigf.log("city build start");
		BlockPos spawn = level.getRespawnData().pos();
		x0 = spawn.getX() - 51;
		z0 = spawn.getZ() - 51;
		for (int cx = (x0 >> 4) - 1; cx <= ((x0 + SIZE) >> 4) + 1; cx++)
			for (int cz = (z0 >> 4) - 1; cz <= ((z0 + SIZE) >> 4) + 1; cz++) level.getChunk(cx, cz);
		floor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn).getY() - 1;
		Random rnd = new Random(7);
		Block air = Blocks.AIR, stone = Blocks.STONE;
		// terrain: clear above, fill below
		for (int x = x0 - 2; x < x0 + SIZE + 2; x++)
			for (int z = z0 - 2; z < z0 + SIZE + 2; z++) {
				for (int y = floor - 12; y < floor; y++) set(x, y, z, stone);
				for (int y = floor + 1; y <= floor + 48; y++) set(x, y, z, air);
				set(x, floor, z, B_BLACK_CONCRETE);
			}
		// roads: dashes, crosswalks, sidewalks
		for (int lx = 0; lx < SIZE; lx++)
			for (int lz = 0; lz < SIZE; lz++) {
				boolean roadX = lx % PITCH < ROAD, roadZ = lz % PITCH < ROAD;
				int x = x0 + lx, z = z0 + lz;
				if (roadX && roadZ) continue;                       // intersection
				if (roadX) {                                        // a road running along z
					int u = lx % PITCH;
					if ((u == 2 || u == 3) && (lz % 6 < 3) && (lz % PITCH) > 6) set(x, floor, z, B_YELLOW_CONCRETE);
					int v = lz % PITCH;
					if ((v == 7 || v == 8) && (u == 0 || u == 1 || u == 4 || u == 5) && (u % 2 == 0 || true) && ((u + lz) % 2 == 0)) set(x, floor, z, B_WHITE_CONCRETE);
				} else if (roadZ) {
					int v = lz % PITCH;
					if ((v == 2 || v == 3) && (lx % 6 < 3) && (lx % PITCH) > 6) set(x, floor, z, B_YELLOW_CONCRETE);
					int u = lx % PITCH;
					if ((u == 7 || u == 8) && ((v + lx) % 2 == 0)) set(x, floor, z, B_WHITE_CONCRETE);
				} else {
					set(x, floor, z, B_LIGHT_GRAY_CONCRETE);   // lot / sidewalk
				}
			}
		// lots
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				int lx = x0 + ROAD + i * PITCH, lz = z0 + ROAD + j * PITCH;
				int kind = rnd.nextInt(10) < 5 ? 0 : (rnd.nextInt(10) < 5 ? 1 : 2);
				if (i == 1 && j == 1) kind = 3;     // bank next to the crossroads
				if (i == 2 && j == 2) kind = 2;     // park
				LOTS.add(new int[] {lx, lz, kind});
				switch (kind) {
					case 0 -> tower(lx + 2, lz + 2, 14, 8 + rnd.nextInt(18), rnd);
					case 1 -> shop(lx + 2, lz + 2, 14, 14, 4 + rnd.nextInt(3), rnd);
					case 2 -> park(lx, lz, rnd);
					default -> bank(lx + 2, lz + 2);
				}
				lamps(lx, lz);
			}
		built = true;
		sigf.kit.Sigf.log("city built at " + x0 + "," + z0 + " floor " + floor);
	}

	static void lamps(int lx, int lz) {
		int[][] c = {{0, 0}, {LOT - 1, 0}, {0, LOT - 1}, {LOT - 1, LOT - 1}};
		for (int[] p : c) {
			int x = lx + p[0], z = lz + p[1];
			for (int y = 1; y <= 5; y++) set(x, floor + y, z, Blocks.COBBLESTONE_WALL);
			set(x, floor + 6, z, Blocks.SEA_LANTERN);
		}
	}

	static void tower(int ox, int oz, int w, int h, Random rnd) {
		Block wall = block(WALLS[rnd.nextInt(WALLS.length)]);
		Block trim = block(WALLS[rnd.nextInt(WALLS.length)]);
		box(ox, oz, w, w, h, wall, trim, true);
		// neon crown
		for (int x = ox; x < ox + w; x++) { set(x, floor + h + 1, oz, Blocks.SEA_LANTERN); set(x, floor + h + 1, oz + w - 1, Blocks.SEA_LANTERN); }
		for (int z = oz; z < oz + w; z++) { set(ox, floor + h + 1, z, Blocks.SEA_LANTERN); set(ox + w - 1, floor + h + 1, z, Blocks.SEA_LANTERN); }
		// rooftop water tank
		for (int y = 2; y <= 4; y++) set(ox + 3, floor + h + y, oz + 3, Blocks.IRON_BLOCK);
		set(ox + 3, floor + h + 5, oz + 3, B_RED_CONCRETE);
	}

	static void shop(int ox, int oz, int w, int d, int h, Random rnd) {
		Block wall = block(WALLS[rnd.nextInt(WALLS.length)]);
		Block trim = rnd.nextBoolean() ? B_RED_CONCRETE : B_BLUE_CONCRETE;
		box(ox, oz, w, d, h, wall, trim, false);
		// striped awning over the sidewalk (south face)
		for (int x = ox; x < ox + w; x++) {
			Block c = (x % 2 == 0) ? B_RED_WOOL : B_WHITE_WOOL;
			set(x, floor + 3, oz - 1, c);
		}
		// neon sign band
		for (int x = ox + 2; x < ox + w - 2; x++) set(x, floor + h - 1, oz, Blocks.SEA_LANTERN);
	}

	static void bank(int ox, int oz) {
		int w = 14, h = 9;
		box(ox, oz, w, w, h, B_WHITE_CONCRETE, B_GRAY_CONCRETE, false);
		// columns and a golden roof band
		for (int x = ox; x < ox + w; x += 3) for (int y = 1; y <= 5; y++) set(x, floor + y, oz - 1, Blocks.QUARTZ_PILLAR);
		for (int x = ox - 1; x <= ox + w; x++) set(x, floor + 6, oz - 1, Blocks.GOLD_BLOCK);
		for (int x = ox; x < ox + w; x++) set(x, floor + h + 1, oz, Blocks.GOLD_BLOCK);
		// entrance
		for (int y = 1; y <= 3; y++) for (int x = ox + 6; x <= ox + 7; x++) set(x, floor + y, oz, Blocks.AIR);
	}

	/** A hollow-looking facade: walls with window grid, flat roof, a door on the south side. */
	static void box(int ox, int oz, int w, int d, int h, Block wall, Block trim, boolean tall) {
		Block glass = B_LIGHT_BLUE_STAINED_GLASS;
		Block lit = Blocks.GLOWSTONE;
		Random r = new Random(ox * 31L + oz);
		for (int x = ox; x < ox + w; x++)
			for (int z = oz; z < oz + d; z++)
				for (int y = 1; y <= h; y++) {
					boolean edge = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
					if (!edge) { set(x, floor + y, z, wall); continue; }
					boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
					boolean windowRow = y > 2 && y % 3 != 0 && y < h;
					boolean windowCol = ((x - ox) + (z - oz)) % 3 != 0;
					if (!corner && windowRow && windowCol) set(x, floor + y, z, r.nextInt(5) == 0 ? lit : glass);
					else set(x, floor + y, z, y <= 2 ? trim : wall);
				}
		for (int x = ox; x < ox + w; x++) for (int z = oz; z < oz + d; z++) set(x, floor + h + 1, z, trim);
		// door (south face, z = oz)
		int dx = ox + w / 2;
		for (int y = 1; y <= 2; y++) { set(dx, floor + y, oz, Blocks.GLASS); set(dx + 1, floor + y, oz, Blocks.GLASS); }
	}

	static void park(int lx, int lz, Random rnd) {
		for (int x = lx + 2; x < lx + LOT - 2; x++)
			for (int z = lz + 2; z < lz + LOT - 2; z++) set(x, floor, z, Blocks.GRASS_BLOCK);
		for (int x = lx + 8; x <= lx + 9; x++) for (int z = lz + 2; z < lz + LOT - 2; z++) set(x, floor, z, Blocks.DIRT_PATH);
		int n = 0;
		for (int i = 0; i < 7; i++) {
			int x = lx + 3 + rnd.nextInt(LOT - 6), z = lz + 3 + rnd.nextInt(LOT - 6);
			if (x >= lx + 7 && x <= lx + 10) continue;
			palm(x, z, 5 + rnd.nextInt(4));
			n++;
		}
		// fountain
		int cx = lx + 9, cz = lz + 9;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(cx + dx, floor + 1, cz + dz, Blocks.STONE_BRICKS);
		set(cx, floor + 1, cz, Blocks.WATER);
		set(cx, floor + 2, cz, Blocks.SEA_LANTERN);
	}

	static void palm(int x, int z, int h) {
		BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
		BlockState leaf = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int y = 1; y <= h; y++) set(x, floor + y, z, log);
		int[][] dir = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dir) {
			set(x + d[0], floor + h + 1, z + d[1], leaf);
			set(x + d[0] * 2, floor + h + 1, z + d[1] * 2, leaf);
			set(x + d[0] * 3, floor + h, z + d[1] * 3, leaf);
		}
		set(x, floor + h + 1, z, leaf);
		set(x, floor + h + 2, z, leaf);
		set(x + 1, floor + h, z + 1, leaf); set(x - 1, floor + h, z + 1, leaf); set(x + 1, floor + h, z - 1, leaf); set(x - 1, floor + h, z - 1, leaf);
	}

	// --- Places for people and cars ---

	static final Random R = new Random();

	/** A point on a sidewalk (world coords, entity height). */
	static Vec3 sidewalk() {
		int[] lot = LOTS.get(R.nextInt(LOTS.size()));
		int a = R.nextInt(LOT);
		int side = R.nextInt(4);
		int ox = lot[0], oz = lot[1];
		double x, z;
		switch (side) {
			case 0 -> { x = ox + a; z = oz + 0.5; }
			case 1 -> { x = ox + a; z = oz + LOT - 0.5; }
			case 2 -> { x = ox + 0.5; z = oz + a; }
			default -> { x = ox + LOT - 0.5; z = oz + a; }
		}
		return new Vec3(x + 0.5, floor + 1, z + 0.5);
	}

	static Vec3 onFloor(double x, double z) { return new Vec3(x, floor + 1, z); }
}
