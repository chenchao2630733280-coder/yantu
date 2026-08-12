const fs = require('fs')
const path = require('path')
const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const required = ['app.js','app.json','app.wxss','project.config.json','sitemap.json','services/store.js','assets/kansai-atlas.png']
const errors = []
for (const file of required) if (!fs.existsSync(path.join(root,file))) errors.push(`missing ${file}`)
for (const page of app.pages) for (const ext of ['.js','.wxml','.wxss']) if (!fs.existsSync(path.join(root,page+ext))) errors.push(`missing ${page+ext}`)
const textFiles=[]
function walk(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){if(['output','.git','node_modules','assets'].includes(entry.name))continue;const full=path.join(dir,entry.name);if(entry.isDirectory())walk(full);else if(/\.(js|json|wxml|wxss)$/.test(entry.name))textFiles.push(full)}}
walk(root)
const vm = require('vm')
for(const file of textFiles){const text=fs.readFileSync(file,'utf8');if(/https?:\/\//.test(text))errors.push(`remote URL forbidden in ${path.relative(root,file)}`);if(path.basename(file)!=='validate.js'&&/TODO|FIXME/.test(text))errors.push(`unfinished marker in ${path.relative(root,file)}`)}
for(const file of textFiles.filter(file=>file.endsWith('.js'))){try{new vm.Script(fs.readFileSync(file,'utf8'),{filename:file})}catch(error){errors.push(`syntax error ${path.relative(root,file)}: ${error.message}`)}}
if(errors.length){console.error(errors.join('\n'));process.exit(1)}
console.log(`lint ok: ${app.pages.length} pages, ${textFiles.length} source files`)
