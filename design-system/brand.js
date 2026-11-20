/*
 * La Muela — brand.js (Revision 1)
 * Único lugar donde se define el nombre visible del producto en previews HTML.
 * Uso: <span data-brand-name>La Muela</span>  (el texto inicial es el fallback
 * si JavaScript no corre, así la página sigue siendo legible y accesible).
 * Atributos: <img data-brand-name-attr="alt" alt="La Muela">  reemplaza el atributo indicado.
 * Script clásico autocontenido: funciona con file:// y sin dependencias.
 */
(function () {
  "use strict";

  var BRAND_NAME = "La Muela";

  function applyBrand(root) {
    var scope = root || document;
    var nodes = scope.querySelectorAll("[data-brand-name]");
    for (var i = 0; i < nodes.length; i++) {
      var attr = nodes[i].getAttribute("data-brand-name-attr");
      if (attr) {
        nodes[i].setAttribute(attr, BRAND_NAME);
      } else {
        nodes[i].textContent = BRAND_NAME;
      }
    }
    var attrOnly = scope.querySelectorAll("[data-brand-name-attr]:not([data-brand-name])");
    for (var j = 0; j < attrOnly.length; j++) {
      attrOnly[j].setAttribute(attrOnly[j].getAttribute("data-brand-name-attr"), BRAND_NAME);
    }
    if (document.title.indexOf("{{brand}}") !== -1) {
      document.title = document.title.replace(/\{\{brand\}\}/g, BRAND_NAME);
    }
  }

  window.BRAND = Object.freeze({ name: BRAND_NAME, apply: applyBrand });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { applyBrand(); });
  } else {
    applyBrand();
  }
})();
