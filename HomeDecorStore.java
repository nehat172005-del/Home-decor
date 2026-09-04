import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class HomeDecorStore {
	private static final int PORT = 8080;
	private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();

	public static void main(String[] args) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext("/", HomeDecorStore::home);
		server.createContext("/api/products", HomeDecorStore::products);
		server.createContext("/login", HomeDecorStore::login);
		server.createContext("/register", HomeDecorStore::register);
		server.createContext("/logout", HomeDecorStore::logout);
		server.createContext("/api/orders", HomeDecorStore::orders);
		server.createContext("/login.html", exchange -> staticFile(exchange, "login.html", "text/html; charset=UTF-8"));
		server.createContext("/styles.css", exchange -> staticFile(exchange, "styles.css", "text/css; charset=UTF-8"));
		server.createContext("/login.js", exchange -> staticFile(exchange, "login.js", "application/javascript; charset=UTF-8"));
		server.createContext("/checkout.html", exchange -> staticFile(exchange, "checkout.html", "text/html; charset=UTF-8"));
		server.createContext("/checkout.css", exchange -> staticFile(exchange, "checkout.css", "text/css; charset=UTF-8"));
		server.createContext("/checkout.js", exchange -> staticFile(exchange, "checkout.js", "application/javascript; charset=UTF-8"));
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		System.out.println("Casa & Co. is running at http://localhost:" + PORT);
	}

	private static void home(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
			send(exchange, 405, "text/plain", "Method not allowed");
			return;
		}
		String page = PAGE
				.replace("<button class=\"button\" style=\"width:100%;justify-content:center\">Checkout <span>→</span></button>", "<button class=\"button\" style=\"width:100%;justify-content:center\" onclick=\"checkout()\">Checkout <span>→</span></button>")
				.replace("</body>", "<script>function checkout(){if(!cart.length)return;localStorage.setItem('casaCart',JSON.stringify(cart));window.location.assign('/checkout.html')}</script></body>");
		send(exchange, 200, "text/html; charset=UTF-8", page);
	}

	private static void products(HttpExchange exchange) throws IOException {
		send(exchange, 200, "application/json; charset=UTF-8", PRODUCT_DATA);
	}

	private static void login(HttpExchange exchange) throws IOException {
		if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
			redirect(exchange, "/login.html");
			return;
		}
		if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
			send(exchange, 405, "text/plain", "Method not allowed");
			return;
		}
		Map<String, String> form = formData(exchange);
		try {
			String name = DBConnection.login(form.getOrDefault("email", ""), form.getOrDefault("password", ""));
			if (name == null) {
				respondAuthError(exchange, 401, "The email or password is incorrect.");
				return;
			}
			String token = UUID.randomUUID().toString();
			SESSIONS.put(token, name);
			exchange.getResponseHeaders().add("Set-Cookie", "CASA_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
			if (acceptsJson(exchange)) send(exchange, 200, "application/json; charset=UTF-8", "{\"success\":true}");
			else redirect(exchange, "/");
		} catch (SQLException exception) {
			exception.printStackTrace();
			respondAuthError(exchange, 503, "Database unavailable. Check your MySQL settings.");
		}
	}

	private static void register(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
			send(exchange, 405, "text/plain", "Method not allowed");
			return;
		}
		Map<String, String> form = formData(exchange);
		String name = form.getOrDefault("name", "").trim();
		String email = form.getOrDefault("email", "").trim();
		String password = form.getOrDefault("password", "");
		if (name.isBlank() || email.isBlank() || password.length() < 8) {
			respondAuthError(exchange, 400, "Use a name, email, and password of at least 8 characters.");
			return;
		}
		try {
			if (!DBConnection.register(name, email, password)) {
				respondAuthError(exchange, 409, "An account already exists for that email.");
				return;
			}
			if (acceptsJson(exchange)) send(exchange, 201, "application/json; charset=UTF-8", "{\"success\":true}");
			else redirect(exchange, "/login");
		} catch (SQLException exception) {
			exception.printStackTrace();
			respondAuthError(exchange, 503, "Database unavailable. Check your MySQL settings.");
		}
	}

	private static void orders(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
			send(exchange, 405, "text/plain", "Method not allowed");
			return;
		}
		Map<String, String> form = formData(exchange);
		String name = form.getOrDefault("name", "").trim();
		String email = form.getOrDefault("email", "").trim();
		String address = form.getOrDefault("address", "").trim();
		try {
			double total = Double.parseDouble(form.getOrDefault("total", "0"));
			if (name.isBlank() || email.isBlank() || address.isBlank() || total <= 0) {
				send(exchange, 400, "application/json; charset=UTF-8", "{\"message\":\"Please complete all checkout details.\"}");
				return;
			}
			long orderId = DBConnection.createOrder(name, email, address, total);
			send(exchange, 201, "application/json; charset=UTF-8", "{\"success\":true,\"orderId\":" + orderId + "}");
		} catch (NumberFormatException exception) {
			send(exchange, 400, "application/json; charset=UTF-8", "{\"message\":\"Invalid order total.\"}");
		} catch (SQLException exception) {
			exception.printStackTrace();
			send(exchange, 503, "application/json; charset=UTF-8", "{\"message\":\"Database unavailable. Run the updated schema.sql first.\"}");
		}
	}

	private static void respondAuthError(HttpExchange exchange, int status, String message) throws IOException {
		if (acceptsJson(exchange)) send(exchange, status, "application/json; charset=UTF-8", "{\"success\":false,\"message\":\"" + message + "\"}");
		else send(exchange, status, "text/html; charset=UTF-8", LOGIN_PAGE.replace("{{MESSAGE}}", message));
	}

	private static boolean acceptsJson(HttpExchange exchange) {
		return exchange.getRequestHeaders().getFirst("Accept") != null && exchange.getRequestHeaders().getFirst("Accept").contains("application/json");
	}

	private static void staticFile(HttpExchange exchange, String filename, String contentType) throws IOException {
		if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
			send(exchange, 405, "text/plain", "Method not allowed");
			return;
		}
		Path file = Paths.get(filename).toAbsolutePath().normalize();
		if (!Files.exists(file)) {
			send(exchange, 404, "text/plain", "File not found");
			return;
		}
		send(exchange, 200, contentType, Files.readString(file));
	}

	private static void logout(HttpExchange exchange) throws IOException {
		String token = cookie(exchange, "CASA_SESSION");
		if (token != null) SESSIONS.remove(token);
		exchange.getResponseHeaders().add("Set-Cookie", "CASA_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
		redirect(exchange, "/");
	}

	private static Map<String, String> formData(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, String> values = new HashMap<>();
		for (String pair : body.split("&")) {
			String[] parts = pair.split("=", 2);
			if (parts.length == 2) values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
		}
		return values;
	}

	private static String cookie(HttpExchange exchange, String name) {
		String header = exchange.getRequestHeaders().getFirst("Cookie");
		if (header == null) return null;
		for (String value : header.split(";")) {
			String[] parts = value.trim().split("=", 2);
			if (parts.length == 2 && parts[0].equals(name)) return parts[1];
		}
		return null;
	}

	private static void redirect(HttpExchange exchange, String location) throws IOException {
		exchange.getResponseHeaders().add("Location", location);
		exchange.sendResponseHeaders(303, -1);
		exchange.close();
	}

	private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.sendResponseHeaders(status, data.length);
		try (OutputStream output = exchange.getResponseBody()) { output.write(data); }
	}

	private static final String PRODUCT_DATA = """
			[{"id":1,"name":"Sculptural Table Lamp","category":"Lighting","price":68,"tag":"New","image":"https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=85"},
			 {"id":2,"name":"Linen Lounge Chair","category":"Furniture","price":249,"tag":"Best seller","image":"https://images.unsplash.com/photo-1567538096630-e0c55bd6374c?auto=format&fit=crop&w=900&q=85"},
			 {"id":3,"name":"Hand-thrown Stoneware","category":"Tabletop","price":42,"tag":"Bestseller","image":"https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?auto=format&fit=crop&w=900&q=85"},
			 {"id":4,"name":"Boucle Cloud Cushion","category":"Textiles","price":36,"tag":"Soft touch","image":"https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=900&q=85"},
			 {"id":5,"name":"Travertine Side Table","category":"Furniture","price":184,"tag":"Limited","image":"https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=900&q=85"},
			 {"id":6,"name":"Arch Mirror","category":"Decor","price":128,"tag":"Made to last","image":"https://images.unsplash.com/photo-1618220179428-22790b461013?auto=format&fit=crop&w=900&q=85"}]
			""";

	private static final String LOGIN_PAGE = """
			<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Account | Casa & Co.</title>
			<style>:root{--ink:#202521;--paper:#f6f1e9;--cream:#fffdf9;--clay:#c9674b;--line:#ded8ce}*{box-sizing:border-box}body{margin:0;min-height:100vh;background:var(--paper);color:var(--ink);font-family:Arial,sans-serif;display:grid;place-items:center;padding:24px}.shell{display:grid;grid-template-columns:1fr 1fr;width:min(900px,100%);background:var(--cream);box-shadow:0 20px 60px #20252118}.visual{background:var(--clay) url('https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=900&q=85') center/cover;min-height:580px;padding:32px;color:white;display:flex;align-items:end}.visual h1{font:500 52px Georgia,serif;line-height:.98;margin:0;max-width:300px}.form{padding:58px 12%;align-self:center}.brand{font:600 26px Georgia,serif}.brand span{color:var(--clay)}h2{font:500 38px Georgia,serif;margin:70px 0 10px}.sub{color:#777;font-size:14px;line-height:1.6;margin-bottom:28px}.message{background:#f8dfd5;color:#9b422c;padding:12px;font-size:13px;margin-bottom:18px}label{display:block;font-size:11px;text-transform:uppercase;letter-spacing:.1em;margin:17px 0 7px}input{width:100%;padding:13px;border:1px solid var(--line);background:transparent;font:inherit}input:focus{outline:2px solid var(--clay);border-color:transparent}.button{margin-top:24px;width:100%;padding:14px;background:var(--ink);border:0;color:white;text-transform:uppercase;letter-spacing:.1em;font-size:11px;font-weight:bold;cursor:pointer}.button:hover{background:var(--clay)}.switch{border:0;background:none;color:var(--clay);font-size:13px;padding:22px 0 0;cursor:pointer}@media(max-width:700px){.shell{display:block}.visual{min-height:230px}.visual h1{font-size:38px}.form{padding:35px 9%}h2{margin-top:35px}}</style></head><body><div class="shell"><div class="visual"><h1>Come home to what matters.</h1></div><div class="form"><a class="brand" href="/">casa <span>&</span> co.</a><h2 id="title">Welcome back.</h2><p class="sub" id="subtitle">Sign in to keep your considered pieces close.</p><p class="message" id="message">{{MESSAGE}}</p><form method="post" action="/login" id="loginForm"><label for="email">Email address</label><input id="email" name="email" type="email" required autocomplete="email"><label for="password">Password</label><input id="password" name="password" type="password" required autocomplete="current-password"><button class="button" type="submit">Sign in</button></form><form method="post" action="/register" id="registerForm" hidden><label for="name">Your name</label><input id="name" name="name" type="text" autocomplete="name"><label for="regEmail">Email address</label><input id="regEmail" name="email" type="email" autocomplete="email"><label for="regPassword">Password</label><input id="regPassword" name="password" type="password" minlength="8" autocomplete="new-password"><button class="button" type="submit">Create account</button></form><button class="switch" id="switch" type="button">New here? Create an account</button></div></div><script>const message=document.querySelector('#message');if(message.textContent.trim()==='{{MESSAGE}}')message.hidden=true;document.querySelector('#switch').onclick=()=>{const login=document.querySelector('#loginForm'),register=document.querySelector('#registerForm'),isLogin=!login.hidden;login.hidden=isLogin;register.hidden=!isLogin;document.querySelector('#title').textContent=isLogin?'Create your account.':'Welcome back.';document.querySelector('#subtitle').textContent=isLogin?'A home for the things you love.':'Sign in to keep your considered pieces close.';document.querySelector('#switch').textContent=isLogin?'Already have an account? Sign in':'New here? Create an account'};</script></body></html>
			""";

	private static final String PAGE = """
			<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
			<title>Casa & Co. | Home, thoughtfully made</title>
			<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
			<link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Playfair+Display:ital,wght@0,500;0,600;1,500&display=swap" rel="stylesheet">
			<style>
			:root{--ink:#202521;--paper:#f6f1e9;--cream:#fffdf9;--clay:#c9674b;--sage:#a7b5a0;--line:#ded8ce}*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;color:var(--ink);background:var(--paper);font-family:'DM Sans',sans-serif}a{color:inherit;text-decoration:none}button{color:inherit;font:inherit;cursor:pointer}.topbar{background:var(--ink);color:#fffaf2;font-size:11px;letter-spacing:.12em;text-align:center;padding:10px;text-transform:uppercase}header{display:flex;justify-content:space-between;align-items:center;max-width:1280px;margin:auto;padding:25px 5vw}.brand{font:600 27px 'Playfair Display',serif;letter-spacing:-.03em}.brand span{color:var(--clay)}nav{display:flex;gap:30px;font-size:13px}nav a:hover{color:var(--clay)}.head-actions{display:flex;gap:10px;align-items:center}.icon-btn{border:0;background:transparent;padding:9px;font-size:19px;position:relative}.count{position:absolute;top:0;right:0;background:var(--clay);color:white;border-radius:50%;min-width:16px;height:16px;font-size:10px;display:grid;place-items:center}
			.hero{max-width:1280px;margin:0 auto;padding:20px 5vw 84px;display:grid;grid-template-columns:1fr 1.25fr;gap:6vw;align-items:center}.eyebrow{text-transform:uppercase;letter-spacing:.19em;font-size:11px;font-weight:700;color:var(--clay);margin:0 0 22px}h1{font:500 clamp(48px,6vw,88px)/.98 'Playfair Display',serif;letter-spacing:-.055em;margin:0 0 25px;max-width:600px}.hero-copy{color:#68645d;line-height:1.7;max-width:380px;font-size:15px;margin-bottom:32px}.button{display:inline-flex;gap:13px;align-items:center;border:1px solid var(--ink);background:var(--ink);color:white;padding:14px 20px;font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;transition:transform .2s,background .2s}.button:hover{transform:translateY(-3px);background:var(--clay);border-color:var(--clay)}.button.light{background:transparent;color:var(--ink)}.hero-image{position:relative;aspect-ratio:1.05;background:#d7d5cb;overflow:hidden}.hero-image img{width:100%;height:100%;object-fit:cover}.hero-note{position:absolute;bottom:20px;left:20px;padding:12px 15px;background:var(--cream);font-size:11px;letter-spacing:.08em;text-transform:uppercase}.ticker{overflow:hidden;background:var(--sage);padding:14px 0;white-space:nowrap;font-size:12px;letter-spacing:.12em;text-transform:uppercase}.ticker-track{display:inline-block;animation:slide 24s linear infinite}.ticker b{margin:0 34px;color:var(--clay)}@keyframes slide{to{transform:translateX(-30%)}}
			section{max-width:1280px;margin:auto;padding:92px 5vw}.section-head{display:flex;justify-content:space-between;align-items:end;margin-bottom:34px}h2{font:500 46px/1 'Playfair Display',serif;letter-spacing:-.04em;margin:0}.section-intro{color:#6f6a61;font-size:14px;max-width:270px;line-height:1.5}.categories{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.category{min-height:210px;padding:22px;display:flex;align-items:end;position:relative;overflow:hidden;color:white;background:#777}.category:before{content:'';position:absolute;inset:0;background:linear-gradient(0deg,#0009,transparent 70%);z-index:1}.category img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;transition:transform .5s}.category:hover img{transform:scale(1.06)}.category strong{z-index:2;font:500 24px 'Playfair Display',serif}.products{display:grid;grid-template-columns:repeat(3,1fr);gap:30px 16px}.product-image{background:#e5dfd5;aspect-ratio:1/.98;overflow:hidden;position:relative}.product-image img{width:100%;height:100%;object-fit:cover;transition:transform .5s}.product:hover img{transform:scale(1.04)}.tag{position:absolute;top:14px;left:14px;padding:7px 10px;background:var(--cream);font-size:10px;text-transform:uppercase;letter-spacing:.08em}.product-info{padding:15px 2px;display:flex;justify-content:space-between;gap:10px}.product-info h3{font:500 18px 'Playfair Display',serif;margin:0 0 5px}.product-info p{color:#817c72;font-size:12px;margin:0}.price{font-size:13px;font-weight:700;white-space:nowrap}.add{border:1px solid var(--line);background:transparent;width:100%;padding:11px;font-size:11px;text-transform:uppercase;letter-spacing:.12em}.add:hover{background:var(--ink);color:white;border-color:var(--ink)}
			.story{max-width:none;background:var(--clay);color:#fff9ef;display:grid;grid-template-columns:1fr 1fr;padding:0}.story img{width:100%;height:100%;min-height:470px;object-fit:cover}.story-copy{padding:80px 8vw;align-self:center}.story h2{max-width:420px}.story p{max-width:390px;line-height:1.75;color:#f6ded5;font-size:15px;margin:26px 0 30px}.story .button{background:#fff9ef;color:var(--ink);border-color:#fff9ef}footer{background:var(--ink);color:#fffaf2;padding:50px 5vw 26px}.footer-inner{max-width:1280px;margin:auto;display:flex;justify-content:space-between;gap:30px}footer p{color:#a7a39b;font-size:13px;line-height:1.6;max-width:280px}footer small{display:block;max-width:1280px;margin:65px auto 0;color:#77756f;border-top:1px solid #3c403b;padding-top:20px;font-size:11px}
			.drawer{position:fixed;z-index:5;top:0;right:0;height:100%;width:min(400px,100%);background:var(--cream);padding:28px;transform:translateX(100%);transition:transform .3s;box-shadow:-10px 0 30px #0002}.drawer.open{transform:none}.drawer-head{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--line);padding-bottom:18px}.drawer h2{font-size:28px}.close{border:0;background:none;font-size:25px}.cart-line{display:flex;gap:15px;align-items:center;padding:18px 0;border-bottom:1px solid var(--line)}.cart-line img{width:65px;height:65px;object-fit:cover}.cart-line h4{margin:0 0 5px;font:500 16px 'Playfair Display',serif}.cart-line p{margin:0;color:#777;font-size:12px}.cart-total{display:flex;justify-content:space-between;font-weight:700;margin:24px 0}.empty{color:#777;font-size:14px;padding:45px 0;text-align:center}
			@media(max-width:760px){header{padding:20px 6vw}nav{display:none}.hero{display:flex;flex-direction:column;align-items:stretch;padding:25px 6vw 60px;gap:40px}h1{font-size:58px}section{padding:65px 6vw}.section-head{display:block}.section-intro{margin-top:15px}.categories{grid-template-columns:repeat(2,1fr)}.category{min-height:160px}.products{grid-template-columns:repeat(2,1fr);gap:25px 10px}.product-info{display:block}.price{display:block;margin-top:7px}.story{display:block}.story img{min-height:300px;max-height:380px}.story-copy{padding:58px 8vw}.footer-inner{display:block}}@media(max-width:430px){h1{font-size:51px}h2{font-size:38px}.products{grid-template-columns:1fr}.brand{font-size:23px}}
			</style></head><body>
			<div class="topbar">Free delivery on orders over $100 <b>·</b> Designed for living well</div><header><a class="brand" href="#top">casa <span>&</span> co.</a><nav><a href="#shop">Shop</a><a href="#collections">Collections</a><a href="#journal">Our journal</a></nav><div class="head-actions"><button class="icon-btn" aria-label="Search">⌕</button><button class="icon-btn" id="cartButton" aria-label="Open cart">♧<span class="count" id="cartCount">0</span></button></div></header>
			<main id="top"><div class="hero"><div><p class="eyebrow">The autumn edit / 2026</p><h1>Make room for beautiful things.</h1><p class="hero-copy">Considered pieces for slow mornings, long dinners, and every little ritual in between.</p><a class="button" href="#shop">Explore the collection <span>↗</span></a></div><div class="hero-image"><img src="https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1400&q=85" alt="Warm, art-filled living room"><div class="hero-note">New shapes, familiar feeling</div></div></div>
			<div class="ticker"><span class="ticker-track">Objects with a point of view <b>✳</b> Made for the everyday <b>✳</b> Thoughtfully sourced <b>✳</b> Objects with a point of view <b>✳</b> Made for the everyday <b>✳</b></span></div>
			<section id="collections"><div class="section-head"><h2>Find your feeling.</h2><p class="section-intro">A little inspiration for wherever you are in your home story.</p></div><div class="categories"><a class="category" href="#shop"><img src="https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=700&q=80" alt="Furniture"><strong>Furniture</strong></a><a class="category" href="#shop"><img src="https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=700&q=80" alt="Lighting"><strong>Lighting</strong></a><a class="category" href="#shop"><img src="https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?auto=format&fit=crop&w=700&q=80" alt="Tabletop"><strong>Tabletop</strong></a><a class="category" href="#shop"><img src="https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=700&q=80" alt="Textiles"><strong>Textiles</strong></a></div></section>
			<section id="shop"><div class="section-head"><h2>Just in.</h2><a class="button light" href="#shop">View all pieces <span>→</span></a></div><div class="products" id="products"></div></section>
			<section class="story" id="journal"><img src="https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1100&q=85" alt="Sunlit reading corner with a chair and plant"><div class="story-copy"><p class="eyebrow" style="color:#f9c8b9">A note from Casa</p><h2>The art of the in-between.</h2><p>Our homes are never finished. They gather stories, soften at the edges, and make space for the people we love. Choose pieces that do the same.</p><a class="button" href="#collections">Read our journal <span>↗</span></a></div></section></main>
			<footer><div class="footer-inner"><div><a class="brand" href="#top">casa <span>&</span> co.</a><p>Good things for the spaces that hold your life.</p></div><div><p>Questions? hello@casaandco.example<br>Mon-Fri, 9am-5pm</p></div></div><small>© 2026 Casa & Co. All rights reserved.</small></footer>
			<aside class="drawer" id="drawer"><div class="drawer-head"><h2>Your bag</h2><button class="close" id="closeCart" aria-label="Close cart">×</button></div><div id="cartItems"><p class="empty">Your bag is waiting for something lovely.</p></div><div id="cartSummary" hidden><div class="cart-total"><span>Total</span><span id="cartTotal">$0</span></div><button class="button" style="width:100%;justify-content:center">Checkout <span>→</span></button></div></aside>
			<script>let products=[],cart=[];const money=value=>'$'+value.toLocaleString('en-US');fetch('/api/products').then(response=>response.json()).then(data=>{products=data;renderProducts()});function renderProducts(){document.querySelector('#products').innerHTML=products.map(product=>`<article class="product"><div class="product-image"><img src="${product.image}" alt="${product.name}"><span class="tag">${product.tag}</span></div><div class="product-info"><div><h3>${product.name}</h3><p>${product.category}</p></div><span class="price">${money(product.price)}</span></div><button class="add" onclick="addToCart(${product.id})">Add to bag +</button></article>`).join('')}function addToCart(id){cart.push(products.find(item=>item.id===id));renderCart();document.querySelector('#drawer').classList.add('open')}function renderCart(){document.querySelector('#cartCount').textContent=cart.length;document.querySelector('#cartItems').innerHTML=cart.length?cart.map(item=>`<div class="cart-line"><img src="${item.image}" alt=""><div><h4>${item.name}</h4><p>${money(item.price)}</p></div></div>`).join(''):'<p class="empty">Your bag is waiting for something lovely.</p>';const total=cart.reduce((sum,item)=>sum+item.price,0);document.querySelector('#cartTotal').textContent=money(total);document.querySelector('#cartSummary').hidden=!cart.length}document.querySelector('#cartButton').onclick=()=>document.querySelector('#drawer').classList.add('open');document.querySelector('#closeCart').onclick=()=>document.querySelector('#drawer').classList.remove('open');</script></body></html>
			""";
}
