(function(){
'use strict';
var root=document.documentElement,content=document.getElementById('reader-content'),session=root.dataset.readerSession;
var initial=JSON.parse(decodeURIComponent(root.dataset.readerCommand));
function post(value){value.session=session;window.ReaderBridge.postMessage(JSON.stringify(value));}
function fail(code){post({kind:'error',request:initial.request,code:code||'renderer'});}
var currentAnchor=0,userScrolling=false,scrollTimer=null;
function fraction(){var maximum=Math.max(0,content.scrollHeight-content.clientHeight);return maximum?Math.max(0,Math.min(1,content.scrollTop/maximum)):0;}
function matchIndex(text,query,anchor,ordinal){
 var at=text.indexOf(query),index=0,best=-1,distance=Infinity;
 while(at>=0){
  if(ordinal>=0&&index===ordinal)return at;
  var difference=Math.abs(at-anchor);if(difference<distance){distance=difference;best=at;}
  at=text.indexOf(query,at+1);index++;
 }
 return ordinal>=0?-1:best;
}
function highlight(query,anchor,ordinal){
 Array.prototype.forEach.call(deck.querySelectorAll('mark.reader-match'),function(mark){mark.replaceWith(document.createTextNode(mark.textContent));});
 deck.normalize();
 if(!query)return null;
 var walk=document.createTreeWalker(deck,NodeFilter.SHOW_TEXT,null,false),node,parts=[],text='';
 while((node=walk.nextNode())){if(node.parentElement.closest('style,script'))continue;parts.push({node:node,start:text.length,end:text.length+node.length});text+=node.nodeValue;}
 var best=matchIndex(text,query,anchor,ordinal);
 if(best<0)return null;
 var first=null;
 parts.forEach(function(part){var start=Math.max(best,part.start),end=Math.min(best+query.length,part.end);if(start>=end)return;
  var range=document.createRange();range.setStart(part.node,start-part.start);range.setEnd(part.node,end-part.start);
  var mark=document.createElement('mark');mark.className='reader-match';mark.style.cssText='background:#fff;color:#000;outline:2px solid #000';
  range.surroundContents(mark);if(!first)first=mark;
 });
 return first;
}
window.ReaderHost={command:function(value){if(value.kind==='cancel')return;try{
 userScrolling=false;currentAnchor=Math.max(0,Number(value.anchor)||0);
 var ordinal=Number.isInteger(value.matchOrdinal)?value.matchOrdinal:-1;
 var match=highlight(String(value.query||'').slice(0,256),currentAnchor,ordinal);
 var saved=Math.max(0,Math.min(1,Number(value.fraction)||0));
 content.scrollTop=saved*Math.max(0,content.scrollHeight-content.clientHeight);
 if(match){var rect=match.getBoundingClientRect(),box=content.getBoundingClientRect();content.scrollTop+=rect.top-box.top-8;content.scrollLeft+=Math.max(0,rect.left-box.left-8);}
 post({kind:'ready',request:value.request,anchor:currentAnchor,fraction:fraction(),page:0,pages:1});
}catch(e){fail('renderer');}}};
content.addEventListener('touchstart',function(){userScrolling=true;},{passive:true});
content.addEventListener('scroll',function(){if(!userScrolling)return;clearTimeout(scrollTimer);scrollTimer=setTimeout(function(){userScrolling=false;
 post({kind:'locate',anchor:currentAnchor,fraction:fraction(),page:0,pages:1});
},180);},{passive:true});
content.style.cssText='margin:8px;width:calc(100vw - 16px);height:calc(100vh - 52px);column-width:auto;column-count:auto;overflow:auto';
var note=document.createElement('div');note.textContent='复杂图形、图表、EMF/WMF、嵌入及音视频对象可能未显示；可切换文字提纲';
note.style.cssText='position:fixed;bottom:0;left:0;right:0;z-index:10;font-size:12px;line-height:1.4;border-top:1px solid #000;padding:4px;background:white;color:black';
document.body.appendChild(note);
var deck=document.createElement('div');deck.id='reader-slide';content.insertBefore(deck,content.firstChild);
window.JSZipUtils={getBinaryContent:function(url,done){fetch(url).then(function(r){if(!r.ok)throw Error('source');return r.arrayBuffer();}).then(function(b){done(null,b);}).catch(function(){fail('corrupt');});}};
window.addEventListener('error',function(){fail('renderer');});
var observer=new MutationObserver(function(){
 if(deck.querySelectorAll('*').length>50000){observer.disconnect();deck.textContent='';fail('limit');return;}
 var slide=deck.querySelector('.slide');if(!slide)return;
 observer.disconnect();
 if(deck.querySelectorAll('.slide').length!==1){fail('limit');return;}
 // Documents never supply executable attributes, active URLs or interactive objects.
 Array.prototype.forEach.call(deck.querySelectorAll('*'),function(node){
  Array.prototype.slice.call(node.attributes).forEach(function(a){if(/^on/i.test(a.name))node.removeAttribute(a.name);});
 });
 Array.prototype.forEach.call(deck.querySelectorAll('script,iframe,object,embed,audio,video'),function(n){n.remove();});
 Array.prototype.forEach.call(deck.querySelectorAll('a'),function(n){n.removeAttribute('href');});
 Array.prototype.forEach.call(deck.querySelectorAll('img'),function(img){img.onerror=function(){img.alt='[图片未显示]';};});
 var scale=Math.min(1,(content.clientWidth-4)/Math.max(1,slide.offsetWidth));
 slide.style.zoom=String(scale);slide.style.margin='0';
 var wrapper=deck.querySelector('#all_slides_warpper');if(wrapper)wrapper.style.height='auto';
 var images=Array.prototype.slice.call(deck.querySelectorAll('img')).map(function(img){return new Promise(function(resolve){if(img.complete)resolve();else{img.onload=resolve;img.onerror=function(){img.alt='[图片未显示]';resolve();};}});});
 Promise.all(images).then(function(){return document.fonts?document.fonts.ready:Promise.resolve();}).then(function(){requestAnimationFrame(function(){window.ReaderHost.command(initial);});});
});
observer.observe(deck,{childList:true,subtree:true});
// PPTXjs HTML is parsed in an inert template and sanitized before mounting.
var append=$.fn.append;
$.fn.append=function(){
 if(this[0]===deck&&typeof arguments[0]==='string'){
  var inert=document.createElement('template');inert.innerHTML=arguments[0];
  Array.prototype.forEach.call(inert.content.querySelectorAll('script,iframe,object,embed,link,meta,audio,video'),function(n){n.remove();});
  Array.prototype.forEach.call(inert.content.querySelectorAll('*'),function(n){
   Array.prototype.slice.call(n.attributes).forEach(function(a){
    if(/^on/i.test(a.name)||a.name==='href'||a.name==='xlink:href'||(a.name==='src'&&!/^data:image\/(png|jpeg|gif|webp);base64,/i.test(a.value)))n.removeAttribute(a.name);
   });
  });
  return append.call(this,inert.content);
 }
 return append.apply(this,arguments);
};
try{$('#reader-slide').pptxToHtml({pptxFileUrl:'res/source.pptx',slideMode:false,keyBoardShortCut:false,jsZipV2:false,mediaProcess:false});}
catch(e){observer.disconnect();fail('renderer');}
})();
