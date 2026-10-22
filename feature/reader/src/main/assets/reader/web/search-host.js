(function(){
'use strict';
var token=0;
function begin(){return ++token;}
function scan(command,text,post){
 var mine=token,query=String(command.query||'').slice(0,256),offset=Math.max(0,Number(command.searchOffset)||0);
 var total=0,hits=[],from=0;
 function batch(){
  if(mine!==token)return;
  var steps=0,match=-1;
  while(query&&(match=text.indexOf(query,from))>=0){
   if(total>=offset&&hits.length<64)hits.push({anchor:match,snippet:text.slice(match,match+160)});
   total++;from=match+1;
   if(++steps===2048){setTimeout(batch,0);return;}
  }
  post({kind:'searchResults',request:command.request,offset:offset,total:total,hits:hits});
 }
 batch();
}
function mark(root,offset,length){
 Array.prototype.slice.call(root.querySelectorAll('mark[data-reader-hit]')).forEach(function(node){
  var parent=node.parentNode;while(node.firstChild)parent.insertBefore(node.firstChild,node);parent.removeChild(node);
 });
 root.normalize();
 var walk=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false),node,position=0,parts=[];
 while((node=walk.nextNode())){parts.push({node:node,start:position,end:position+node.length});position+=node.length;}
 parts.forEach(function(part){
  var first=Math.max(offset,part.start)-part.start,last=Math.min(offset+length,part.end)-part.start;
  if(last<=first)return;
  var text=part.node;if(last<text.length)text.splitText(last);if(first>0)text=text.splitText(first);
  var mark=document.createElement('mark');mark.setAttribute('data-reader-hit','');
  mark.style.cssText='background:#ddd;color:black;text-decoration:underline';
  text.parentNode.replaceChild(mark,text);mark.appendChild(text);
 });
}
window.ReaderSearch={begin:begin,scan:scan,mark:mark};
})();
