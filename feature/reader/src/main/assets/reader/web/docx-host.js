(function(){
'use strict';
var root=document.documentElement,content=document.getElementById('reader-content'),session=root.dataset.readerSession;
var sections=[],starts=[],text='',page=0,lastQuery='',lastMatch=-1;
function post(value){value.session=session;window.ReaderBridge.postMessage(JSON.stringify(value));}
function anchorPage(anchor){var found=0;for(var i=0;i<starts.length;i++){if(starts[i]<=anchor)found=i;else break;}return found;}
function show(target){
 page=Math.max(0,Math.min(sections.length-1,target));
 sections.forEach(function(section,index){section.style.display=index===page?'block':'none';});
 var section=sections[page];section.style.transform='none';section.style.zoom='1';section.style.margin='0';
 var width=section.offsetWidth;
 section.style.zoom=Math.min(1,(window.innerWidth-16)/Math.max(1,width));
 content.scrollTop=0;content.scrollLeft=0;
}
function ready(command,anchor,found){post({kind:'ready',request:command.request,anchor:anchor,
 fraction:sections.length<=1?0:page/(sections.length-1),page:page,pages:sections.length,found:found!==false});}
function reveal(anchor){
 var walk=document.createTreeWalker(sections[page],NodeFilter.SHOW_TEXT,null,false),node,offset=starts[page];
 while((node=walk.nextNode())){var end=offset+node.length;if(anchor<end){var range=document.createRange();
  range.setStart(node,Math.max(0,anchor-offset));range.setEnd(node,Math.min(node.length,anchor-offset+1));
  content.scrollTop+=range.getBoundingClientRect().top-content.getBoundingClientRect().top-8;break;
 }offset=end;}
}
function command(value){try{
 window.ReaderSearch.begin();
 if(value.kind==='cancel')return;
 if(value.kind==='searchAll'){window.ReaderSearch.scan(value,text,post);return;}
 if(value.kind==='hit')window.ReaderSearch.mark(content,Number(value.anchor)||0,String(value.query||'').length);

 var anchor=Math.max(0,Math.min(text.length,Number(value.anchor)||0));
 if(value.kind==='turn'){show(page+(value.delta<0?-1:1));ready(value,starts[page]);return;}
 if(value.kind==='page'){show(Number(value.page)||0);var restored=anchorPage(anchor)===page?anchor:starts[page];
  reveal(restored);ready(value,restored);return;}
 if(value.kind==='search'){
  var query=String(value.query||'').slice(0,256),from=query===lastQuery&&anchor===lastMatch?anchor+1:anchor;
  var match=query?text.indexOf(query,from):-1;if(match<0){ready(value,anchor,false);return;}
  lastQuery=query;lastMatch=match;show(anchorPage(match));reveal(match);ready(value,match);return;
 }
 show(anchorPage(anchor));reveal(anchor);ready(value,anchor);
}catch(error){post({kind:'error',request:value.request});}}
window.ReaderHost={command:command};
var scrollTimer=null,userScrolling=false,lastSent=-1;
content.addEventListener('touchstart',function(){userScrolling=true;},{passive:true});
content.addEventListener('scroll',function(){
 if(!userScrolling)return;clearTimeout(scrollTimer);scrollTimer=setTimeout(function(){
  userScrolling=false;var walk=document.createTreeWalker(sections[page],NodeFilter.SHOW_TEXT,null,false),node,offset=starts[page];
  var top=content.getBoundingClientRect().top;
  while((node=walk.nextNode())){var range=document.createRange();range.selectNodeContents(node);
   var rect=range.getBoundingClientRect();if(rect.bottom>top&&rect.height>0){if(lastSent!==offset){lastSent=offset;post({kind:'locate',anchor:offset});}break;}
   offset+=node.length;
  }
 },180);
},{passive:true});
var initial=JSON.parse(decodeURIComponent(root.dataset.readerCommand));
content.style.cssText='margin:8px;width:calc(100vw - 16px);height:calc(100vh - 40px);column-width:auto;column-count:auto;overflow:auto';
var style=document.createElement('div');document.body.appendChild(style);
fetch('res/source.docx').then(function(response){if(!response.ok)throw new Error('source');return response.arrayBuffer();})
 .then(function(bytes){return docx.renderAsync(bytes,content,style,{inWrapper:false,breakPages:true,
  renderAltChunks:false,renderComments:false,renderChanges:false,useBase64URL:true});})
 .then(function(){
  if(content.querySelectorAll('*').length>50000)throw new Error('nodes');
  sections=Array.prototype.slice.call(content.querySelectorAll('section.docx'));
  if(!sections.length||sections.length>1000)throw new Error('pages');
  sections.forEach(function(section){starts.push(text.length);text+=section.textContent||'';});
  if(text.length>2000000)throw new Error('text');
  var note=document.createElement('div');note.textContent='原版式 · 本页可滚动；复杂域、字体及浮动对象可能有差异';
  note.style.cssText='position:fixed;bottom:0;font-size:12px;background:white';document.body.appendChild(note);
  var images=Array.prototype.slice.call(content.querySelectorAll('img')).map(function(img){return new Promise(function(resolve){
   if(img.complete)resolve();else{img.onload=resolve;img.onerror=function(){img.alt='图片未显示';resolve();};}
  });});
  return Promise.all(images);
 }).then(function(){return document.fonts?document.fonts.ready:Promise.resolve();})
 .then(function(){requestAnimationFrame(function(){command(initial);});})
 .catch(function(){post({kind:'error',request:initial.request});});
})();
