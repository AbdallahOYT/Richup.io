

 // ==UserScript==
 // @name         Richup.io Palestine Names & Flag Replacer
 // @namespace    3bood-richup
 // @version      2.4.0
 // @description  Replaces Israeli names and flag artwork with Palestinian equivalents.
 // @match        https://richup.io/room/*
 // @grant        none
 // @run-at       document-idle
 // ==/UserScript==

 (() => {
   "use strict";

   const settings = {
     names: {
       Israel: "Palestine",
       "Tel Aviv": "Yafa",
       Haifa: "Akka",
       Jerusalem: "Al Quds",
     },
     flags: {
       Israel: "https://flagdownload.com/wp-content/uploads/Flag_of_Palestine_Flat_Round.png",
     },
   };

   const FLAG_SIZE = "32px";

   const targetSelector =
     '[class*="richup-block-top"], [class*="richup-block-left"], [class*="richup-block-right"], [class*="richup-block-bottom"]';

   const escapeRegExp = value =>
     value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

   const textRegexMap = Object.entries(settings.names)
     .sort(([a], [b]) => b.length - a.length)
     .map(([oldName, newName]) => ({
       regex: new RegExp("\\b" + escapeRegExp(oldName) + "\\b", "gi"),
       newName,
     }));

   function processTextNode(node) {
     if (!node?.nodeValue?.trim()) return;
     if (node.parentElement?.closest("script,style,textarea,input,code")) return;

     let text = node.nodeValue;

     for (const item of textRegexMap) {
       text = text.replace(item.regex, item.newName);
     }

     if (text !== node.nodeValue) {
       node.nodeValue = text;
     }
   }

   function isIsraeliFlagSvg(svg) {
     if (!svg || svg.tagName?.toLowerCase() !== "svg") return false;

     const markup = (svg.outerHTML || "").replace(/\s+/g, " ");

     const hasStar =
       markup.includes("M352 200.575") ||
       markup.includes("M352,200.575") ||
       markup.includes("M295.314 256");

     const hasBlueStripes =
       markup.includes("M0 32h512v64H0z") ||
       markup.includes("M0 416h512v64H0z") ||
       markup.includes("M0 32 H512 V96 H0 Z");

     return hasStar && hasBlueStripes;
   }

   function replaceIsraeliFlag(svg) {
     if (!isIsraeliFlagSvg(svg) || !svg.parentNode) return false;

     const img = document.createElement("img");
     img.src = settings.flags.Israel;
     img.alt = "Palestinian flag";
     img.title = "Palestinian flag";
     img.draggable = false;
     img.referrerPolicy = "no-referrer";

     if (svg.hasAttribute("class")) {
       img.setAttribute("class", svg.getAttribute("class"));
     }

     img.style.setProperty("display", "block", "important");
     img.style.setProperty("width", FLAG_SIZE, "important");
     img.style.setProperty("height", FLAG_SIZE, "important");
     img.style.setProperty("min-width", FLAG_SIZE, "important");
     img.style.setProperty("min-height", FLAG_SIZE, "important");
     img.style.setProperty("max-width", FLAG_SIZE, "important");
     img.style.setProperty("max-height", FLAG_SIZE, "important");
     img.style.setProperty("object-fit", "contain", "important");
     img.style.setProperty("border-radius", "50%", "important");
     img.style.setProperty("flex-shrink", "0", "important");

     svg.parentNode.replaceChild(img, svg);
     return true;
   }

   function walkDOM(node) {
     if (!node) return;

     if (node.nodeType === Node.TEXT_NODE) {
       processTextNode(node);
       return;
     }

     if (
       node.nodeType !== Node.ELEMENT_NODE &&
       node.nodeType !== Node.DOCUMENT_NODE
     ) return;

     const tag = node.tagName?.toUpperCase();

     if (["SCRIPT", "STYLE", "TEXTAREA", "INPUT", "IMG"].includes(tag)) {
       return;
     }

     if (tag === "SVG" && replaceIsraeliFlag(node)) return;

     for (const child of Array.from(node.childNodes || [])) {
       walkDOM(child);
     }
   }

   function resizeReplacedImageContainers() {
     document.querySelectorAll(targetSelector).forEach(tile => {
       const iconContainer = tile.querySelectorAll("div")[4];

       if (!iconContainer || iconContainer.hasAttribute("data-abood-resized")) {
         return;
       }

       const children = Array.from(iconContainer.childNodes).filter(
         node =>
           !(node.nodeType === Node.TEXT_NODE && !node.textContent.trim())
       );

       if (
         children.length === 1 &&
         children[0].nodeType === Node.ELEMENT_NODE &&
         children[0].tagName === "IMG" &&
         children[0].alt === "Palestinian flag"
       ) {
         iconContainer.style.setProperty("width", FLAG_SIZE, "important");
         iconContainer.style.setProperty("height", FLAG_SIZE, "important");
         iconContainer.style.setProperty("min-width", FLAG_SIZE, "important");
         iconContainer.style.setProperty("min-height", FLAG_SIZE, "important");
         iconContainer.style.setProperty("flex-shrink", "0", "important");
         iconContainer.style.setProperty("overflow", "visible", "important");
         iconContainer.setAttribute("data-abood-resized", "true");
       }
     });
   }

   function initialize() {
     if (!document.body) {
       setTimeout(initialize, 250);
       return;
     }

     walkDOM(document.body);
     resizeReplacedImageContainers();

     let queued = false;

     const observer = new MutationObserver(mutations => {
       if (queued) return;
       queued = true;

       requestAnimationFrame(() => {
         queued = false;

         for (const mutation of mutations) {
           if (mutation.type === "characterData") {
             processTextNode(mutation.target);
           }

           for (const added of mutation.addedNodes || []) {
             walkDOM(added);
           }
         }

         resizeReplacedImageContainers();
       });
     });

     observer.observe(document.body, {
       childList: true,
       subtree: true,
       characterData: true,
     });

     console.log("Richup Palestine replacer initialized.");
   }

   const waitTimer = setInterval(() => {
     if (document.querySelector(targetSelector)) {
       clearInterval(waitTimer);
       initialize();
     }
   }, 500);
 })();
