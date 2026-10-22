(function(){
'use strict';
var root=document.documentElement,content=document.getElementById('reader-content'),session=root.dataset.readerSession;
var page=0,pages=1,step=1,nodes=[],text='',layoutRight=0,lastQuery='',lastMatch=-1;
function post(data){data.session=session;window.ReaderBridge.postMessage(JSON.stringify(data));}
function indexText(){nodes=[];text='';layoutRight=0;var walk=document.createTreeWalker(content,NodeFilter.SHOW_TEXT,null,false),node;
 while((node=walk.nextNode())){var value=node.nodeValue||'';if(value.length){var range=document.createRange();range.selectNodeContents(node);var rects=range.getClientRects(),visible=false,table=node.parentElement.closest('table');for(var j=0;j<rects.length;j++){if(rects[j].width>0&&rects[j].height>0){visible=true;layoutRight=Math.max(layoutRight,table?table.getBoundingClientRect().right:rects[j].right);}}nodes.push({node:node,start:text.length,end:text.length+value.length,visible:visible,fallback:-1});text+=value;}}
 var next=-1,previous=-1;for(var i=nodes.length-1;i>=0;i--){if(nodes[i].visible)next=i;nodes[i].fallback=next;}for(var k=0;k<nodes.length;k++){if(nodes[k].visible)previous=k;if(nodes[k].fallback<0)nodes[k].fallback=previous;}}

function targetAt(offset){if(!nodes.length)return null;offset=Math.max(0,Math.min(text.length-1,offset));var lo=0,hi=nodes.length-1;
 while(lo<hi){var mid=Math.floor((lo+hi)/2);if(nodes[mid].end<=offset)lo=mid+1;else hi=mid;}
 var original=nodes[lo];if(original.fallback<0)return null;var part=nodes[original.fallback];
 return {node:part.node,local:part===original?offset-part.start:(original.fallback>lo?0:part.node.length-1)};}
function rectAt(offset,raw){var target=targetAt(offset);if(!target)return null;var range=document.createRange();
 range.setStart(target.node,target.local);range.setEnd(target.node,Math.min(target.local+1,target.node.length));
 var table=target.node.parentElement.closest('table');return table&&!raw?table.getBoundingClientRect():range.getBoundingClientRect();}
function reveal(offset){var target=targetAt(offset);if(!target)return;var table=target.node.parentElement.closest('table');if(!table)return;
 var rect=rectAt(offset,true),box=table.getBoundingClientRect();
 if(rect.left<box.left||rect.right>box.right)table.scrollLeft+=rect.left-box.left-8;
 if(rect.top<box.top||rect.bottom>box.bottom)table.scrollTop+=rect.top-box.top-8;}
function columnAt(offset){var rect=rectAt(offset);return rect?Math.max(0,Math.floor((rect.left+page*step-16)/step)):0;}
function anchorAt(target){var lo=0,hi=text.length;while(lo<hi){var mid=Math.floor((lo+hi)/2);if(columnAt(mid)<target)lo=mid+1;else hi=mid;}return lo;}
function display(target){page=Math.max(0,Math.min(pages-1,target));content.style.transform='translateX('+(-page*step)+'px)';}
function ready(command,anchor,found){post({kind:'ready',request:command.request,anchor:anchor,fraction:pages<=1?0:page/(pages-1),page:page,pages:pages,found:found!==false});}
function command(value){try{
 window.ReaderSearch.begin();
 if(value.kind==='cancel')return;
 if(value.kind==='searchAll'){window.ReaderSearch.scan(value,text,post);return;}
 if(value.kind==='hit'){window.ReaderSearch.mark(content,Number(value.anchor)||0,String(value.query||'').length);indexText();}

 var anchor=Math.max(0,Math.min(text.length,Number(value.anchor)||0));
 if(value.kind==='turn'){display(page+(value.delta<0?-1:1));var nextAnchor=anchorAt(page);reveal(nextAnchor);ready(value,nextAnchor);return;}
 if(value.kind==='search'){
  var query=String(value.query||'').slice(0,256),current=anchor,from=query===lastQuery&&current===lastMatch?current+1:current;
  var found=query?text.indexOf(query,from):-1;if(found<0){ready(value,anchor,false);return;}
  lastQuery=query;lastMatch=found;display(columnAt(found));reveal(found);ready(value,found);return;
 }
 display(text.length?columnAt(anchor):Math.round((Number(value.fraction)||0)*(pages-1)));reveal(anchor);ready(value,anchor);
 }catch(e){post({kind:'error',request:value.request});}}
window.ReaderHost={command:command};
var touchX=0,touchY=0;content.addEventListener('touchstart',function(e){if(e.touches.length===1){touchX=e.touches[0].clientX;touchY=e.touches[0].clientY;}},{passive:true});
content.addEventListener('touchend',function(e){var target=e.target;
 if(target.closest&&target.closest('table,pre,a'))return;
 var t=e.changedTouches[0];if(!t)return;var dx=t.clientX-touchX,dy=t.clientY-touchY;
 if(Math.abs(dx)>50&&Math.abs(dx)>Math.abs(dy)*1.5)post({kind:'turn',delta:dx<0?1:-1});
},{passive:true});
content.addEventListener('click',function(e){var link=e.target.closest?e.target.closest('a'):null;if(!link)return;e.preventDefault();if(!e.isTrusted)return;
 var href=link.getAttribute('href')||'';if(href.charAt(0)==='#'){var id;try{id=decodeURIComponent(href.slice(1));}catch(ignored){return;}var target=document.getElementById(id);if(target){for(var i=0;i<nodes.length;i++){if(target.contains(nodes[i].node)){post({kind:'locate',anchor:nodes[i].start});break;}}}}else post({kind:'link',url:link.href});});
function boot(){indexText();var images=content.querySelectorAll('img');for(var i=0;i<images.length;i++)layoutRight=Math.max(layoutRight,images[i].getBoundingClientRect().right);step=Math.max(1,window.innerWidth);pages=Math.max(1,Math.ceil((layoutRight-16)/step));command(JSON.parse(decodeURIComponent(root.dataset.readerCommand)));}
var images=Array.prototype.slice.call(content.querySelectorAll('img')).map(function(img){return new Promise(function(resolve){if(img.complete)resolve();else{img.onload=resolve;img.onerror=resolve;}});});
Promise.all(images).then(function(){return document.fonts?document.fonts.ready:Promise.resolve();}).then(function(){requestAnimationFrame(function(){requestAnimationFrame(boot);});});
})();
