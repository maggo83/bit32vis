import * as bitsquiggles from "./bitsquiggles-renderer-canvas.js";

const variants = {
  32: {
    formatHex: bitsquiggles.formatHex32,
    parseHex: bitsquiggles.parseHex32,
    pixels: bitsquiggles.pixels32,
    renderRaster: bitsquiggles.renderRaster32,
    renderSmooth: bitsquiggles.renderSmooth32,
    spec: bitsquiggles.spec32,
  },
  40: {
    formatHex: bitsquiggles.formatHex40,
    parseHex: bitsquiggles.parseHex40,
    pixels: bitsquiggles.pixels40,
    renderRaster: bitsquiggles.renderRaster40,
    renderSmooth: bitsquiggles.renderSmooth40,
    spec: bitsquiggles.spec40,
  },
};
let variant = "32";

const form = document.querySelector("#value-form");
const input = document.querySelector("#value");
const message = document.querySelector("#message");
const random = document.querySelector("#random");
const copyLink = document.querySelector("#copy-link");
const variant32 = document.querySelector("#variant-32");
const variant40 = document.querySelector("#variant-40");
const cards = new Map(
  bitsquiggles.STYLES.map((style) => [
    style,
    document.querySelector(`#${style}-card`),
  ]),
);

function update(value) {
  const api = variants[variant];
  const hex = api.formatHex(value);
  input.value = hex;
  const standard = api.spec(value);
  document.querySelector("#mixed-value").textContent =
    `0x${api.formatHex(standard.mixed)}`;
  document.querySelector("#preferred-mode").textContent =
    standard.preferredMode;
  document.querySelector("#rendered-mode").textContent = standard.actualMode;
  const fallback = document.querySelector("#fallback");
  fallback.hidden = !standard.fallback;
  fallback.textContent = `The preferred symmetry could not encode this value uniquely, so BitSquiggle${variant} used A|.`;
  bitsquiggles.STYLES.forEach((style) => {
    const visual = api.spec(value, style);
    const raster = api.pixels(value, style);
    const card = cards.get(style);
    const smooth = card.querySelector(".smooth");
    const native = card.querySelector(".raster");
    smooth.width = raster.width * 10;
    smooth.height = raster.height * 10;
    native.width = raster.width;
    native.height = raster.height;
    native.setAttribute(
      "aria-label",
      `${style} native ${raster.width} by ${raster.height} pixel raster`,
    );
    card.style.setProperty("--background", visual.background);
    card.style.setProperty("--foreground", visual.foreground);
    api.renderSmooth(smooth, visual);
    api.renderRaster(native, raster);
  });
  const url = new URL(window.location.href);
  url.search = `bits=${variant}&value=${hex}`;
  history.replaceState(null, "", url);
  message.textContent = "";
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  try {
    update(variants[variant].parseHex(input.value));
  } catch (error) {
    message.textContent = error.message;
    input.focus();
  }
});

random.addEventListener("click", () => {
  const words = crypto.getRandomValues(new Uint32Array(2));
  update(
    variant === "32"
      ? words[0]
      : (BigInt(words[0] & 0xff) << 32n) | BigInt(words[1]),
  );
});
    function selectVariant(next, value = input.value) {
  variant = next;
  variant32.setAttribute("aria-pressed", String(next === "32"));
  variant40.setAttribute("aria-pressed", String(next === "40"));
  document.querySelector("#input-label").textContent =
    `Unsigned ${next}-bit hexadecimal value`;
  document.querySelector("#input-help").textContent =
    `Enter up to ${next === "32" ? "eight" : "ten"} hex digits. The value stays in this browser.`;
  document.querySelector("#hero-title").textContent = `BitSquiggle${next}`;
  input.maxLength = next === "32" ? 10 : 12;
  const raw = value.replace(/^0x/i, "").slice(-(next === "32" ? 8 : 10)) || "0";
  update(variants[next].parseHex(raw));
}
variant32.addEventListener("click", () => selectVariant("32"));
variant40.addEventListener("click", () => selectVariant("40"));
copyLink.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(window.location.href);
    message.textContent = "Share link copied.";
  } catch {
    message.textContent = "Copy the address bar to share this visualization.";
  }
});

try {
  const params = new URLSearchParams(window.location.search);
  variant = params.get("bits") === "40" ? "40" : "32";
  selectVariant(variant, params.get("value") || input.value);
} catch {
  selectVariant("32", "12345678");
  message.textContent =
    "The link contained an invalid value; showing the default instead.";
}
