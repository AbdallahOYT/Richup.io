

 // ==UserScript==
 // @name         Richup.io Japan Names & Flag Replacer
 // @namespace    3bood-richup-japan
 // @version      1.2.0
 // @description  Replaces Italian city names and flag with Japanese equivalents.
 // @match        https://richup.io/room/*
 // @grant        none
 // @run-at       document-idle
 // ==/UserScript==

 (() => {
   "use strict";

   const settings = {
     names: {
       Italy: "Japan",
       Rome: "Tokyo",
       Milan: "Yokohama",
       Venice: "Osaka",
     },
     flagUrl: "https://cdn.freeflags.org/flat-circle/japan-flag-flat-circle-512.png",
   };

   const tileSelector =
     '[class*="richup-block-top"], [class*="richup-block-left"], [class*="richup-block-right"], [class*="richup-block-bottom"]';

   const escapeRegExp = s => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

   const replacements = Object.entries(settings.names).map(([from, to]) => ({
     regex: new RegExp("\\b" + escapeRegExp(from) + "\\b", "gi"),
     to,
   }));

   function replaceText(node) {
     if (!node?.nodeValue?.trim()) return;
     if (node.parentElement?.closest("script,style,textarea,input,code")) return;

     let text = node.nodeValue;
     for (const item of replacements) {
       text = text.replace(item.regex, item.to);
     }
     if (text !== node.nodeValue) node.nodeValue = text;
   }

   function isJapanTile(tile) {
     const text = tile.innerText || "";
     return /\b(Italy|Japan|Rome|Tokyo|Milan|Yokohama|Venice|Osaka)\b/i.test(text);
   }

   function replaceTileFlag(tile) {
     if (!isJapanTile(tile)) return;

     // Richup's flag is typically inside this nested tile container.
     const divs = tile.querySelectorAll("div");
     const preferred = divs[4];

     let svg = preferred?.querySelector("svg");

     // Fallback: look for a small SVG in the tile's nested divs.
     if (!svg) {
       svg = Array.from(tile.querySelectorAll("svg")).find(el => {
         const r = el.getBoundingClientRect();
         return r.width > 0 && r.height > 0 && r.width <= 100 && r.height <= 100;
       });
     }

     if (!svg || svg.dataset.japanReplaced === "true") return;

     const img = document.createElement("img");
     img.src = settings.flagUrl;
     img.alt = "Japanese flag";
     img.title = "Japanese flag";
     img.draggable = false;
     img.style.width = svg.getAttribute("width") || "100%";
     img.style.height = svg.getAttribute("height") || "100%";
     img.style.objectFit = "contain";
     img.style.display = "block";

     if (svg.hasAttribute("class")) {
       img.setAttribute("class", svg.getAttribute("class"));
     }

     svg.replaceWith(img);
   }

   function process(root) {
     if (!root) return;

     if (root.nodeType === Node.TEXT_NODE) {
       replaceText(root);
       return;
     }

     if (root.nodeType !== Node.ELEMENT_NODE &&
         root.nodeType !== Node.DOCUMENT_NODE) return;

     if (["SCRIPT", "STYLE", "TEXTAREA", "INPUT", "IMG"].includes(
       root.tagName?.toUpperCase()
     )) return;

     for (const child of Array.from(root.childNodes || [])) {
       process(child);
     }

     if (root.matches?.(tileSelector)) {
       replaceTileFlag(root);
     }
   }

   function initialize() {
     if (!document.body) return;

     process(document.body);

     let queued = false;
     const observer = new MutationObserver(mutations => {
       if (queued) return;
       queued = true;

       requestAnimationFrame(() => {
         queued = false;
         for (const mutation of mutations) {
           if (mutation.type === "characterData") {
             replaceText(mutation.target);
           }
           for (const node of mutation.addedNodes || []) {
             process(node);
           }
         }

         document.querySelectorAll(tileSelector).forEach(replaceTileFlag);
       });
     });

     observer.observe(document.body, {
       childList: true,
       subtree: true,
       characterData: true,
     });

     console.log("Richup Japan replacer initialized.");
   }

   const timer = setInterval(() => {
     if (document.querySelector(tileSelector)) {
       clearInterval(timer);
       initialize();
     }
   }, 500);
 })();
