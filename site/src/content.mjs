// Page content for htmllens.silentdev.online. Plain data — build.mjs turns it into static HTML.
// Write for people, not search engines: say what the page does, then answer real questions.

export const tools = [
  {
    slug: 'html-viewer',
    nav: 'HTML viewer',
    title: 'HTML Viewer Online — Open .html Files Safely | HTML Lens',
    description: 'Open any .html or .htm file in your browser and see it rendered with its images and styles. Private: the file never leaves your device. Free, no sign-up.',
    kicker: 'Free online HTML viewer',
    h1: 'Open an HTML file and see what it really looks like.',
    intro: 'Drop an .html or .htm file below. It opens in a sandbox in your own browser, with a security check, the page source, a console and device previews — nothing is uploaded.',
    accept: '.html,.htm,.xhtml,.svg,.zip',
    steps: [
      ['Choose or drop the file', 'Single pages, or a ZIP or folder when the page has its own images and CSS.'],
      ['It opens in a sandbox', 'Scripts run without internet access by default, so nothing can phone home.'],
      ['Look closer if you need to', 'View source, search the text, check the console, preview phone and tablet sizes.'],
    ],
    features: [
      ['Images and styles stay intact', 'Open the whole folder or ZIP and relative links, CSS, fonts and images load exactly like on the original site.'],
      ['A safety check before you trust it', 'Every file is scanned for fake sign-in pages, hidden code and data being sent to other sites.'],
      ['Three sandbox levels', 'Safe (no scripts), Offline (scripts, no internet) and Online (like a normal browser). You decide.'],
      ['Developer tools on any device', 'Console, network log, find in page, outline and device frames — even on a phone.'],
    ],
    faq: [
      ['Is my file uploaded anywhere?', 'No. The page is read and rendered by your browser. HTML Lens has no server that receives files, so there is nothing to upload to.'],
      ['Why do images not show when I open an .html file on its own?', 'Most saved pages keep their pictures and CSS in a separate folder. Open that folder, or a ZIP of it, and HTML Lens loads everything together.'],
      ['Can I open HTML files on my phone?', 'Yes. The web app works in Safari and Chrome on iPhone and Android. Use the Files app or your downloads to pick the file.'],
      ['Is it really free?', 'Yes, with no account. The core viewer will always be free.'],
    ],
    related: ['mhtml-viewer', 'zip-html-viewer', 'html-attachment-checker', 'run-html-code'],
  },
  {
    slug: 'mhtml-viewer',
    nav: 'MHTML viewer',
    title: 'Open MHT & MHTML Files Online — Free MHTML Viewer | HTML Lens',
    description: 'Open .mhtml and .mht files saved by Chrome, Edge or Internet Explorer on any device, including iPhone, Mac and Android. Private and free — nothing is uploaded.',
    kicker: 'MHT and MHTML viewer',
    h1: 'Open MHTML and MHT files on any device.',
    intro: 'Chrome and Edge save whole web pages as a single .mhtml file, but phones and Macs often refuse to open them. Drop yours here and read it with every image in place.',
    accept: '.mhtml,.mht',
    steps: [
      ['Pick the .mhtml or .mht file', 'From your downloads, email or a USB stick.'],
      ['HTML Lens unpacks it', 'Every image, stylesheet and frame inside the archive is put back together.'],
      ['Read it, or convert it', 'Keep reading here, or save it as one plain .html file that opens anywhere.'],
    ],
    features: [
      ['Works where Safari won’t', 'iPhone, iPad and Mac have no built-in MHTML support. This does.'],
      ['Everything stays offline', 'Saved pages open without reaching out to the original website.'],
      ['Convert to HTML or ZIP', 'Turn an archive into a single self-contained .html file, or a folder of files.'],
      ['Old IE archives too', 'Files saved by Internet Explorer as .mht open the same way.'],
    ],
    faq: [
      ['What is an MHTML file?', 'A web page and everything it needs — pictures, styles, frames — packed into one file, using the same format as email attachments. Chrome calls it “Webpage, Single File”.'],
      ['How do I open an MHTML file on iPhone?', 'Open this page in Safari, tap “Open a file” and pick the .mhtml file from Files. It opens straight away; nothing is uploaded.'],
      ['Can I convert MHTML to HTML?', 'Yes. Use the converter to download a single .html file with images embedded, or a ZIP with separate files.'],
      ['Why does the page look slightly different from the original?', 'Interactive parts that relied on the live website, like comments or live prices, are not stored in the archive.'],
    ],
    related: ['mhtml-to-html', 'webarchive-viewer', 'html-viewer', 'eml-viewer'],
  },
  {
    slug: 'mhtml-to-html',
    nav: 'MHTML to HTML',
    title: 'Convert MHTML to HTML — Free, Private, In Your Browser | HTML Lens',
    description: 'Convert .mhtml, .mht and Safari .webarchive files to a single HTML file or a ZIP of files. Runs in your browser; nothing is uploaded.',
    kicker: 'MHTML to HTML converter',
    h1: 'Turn MHTML into a plain HTML file.',
    intro: 'Drop an .mhtml, .mht or .webarchive file. HTML Lens rebuilds it and lets you save one self-contained .html file — images included — or a ZIP of separate files.',
    accept: '.mhtml,.mht,.webarchive',
    tool: 'convert',
    steps: [
      ['Choose the archive', 'MHTML from Chrome or Edge, MHT from Internet Explorer, or a Safari web archive.'],
      ['Check it looks right', 'The page opens first, so you can see what you’re converting.'],
      ['Save as HTML or ZIP', 'One portable file, or a folder you can edit.'],
    ],
    features: [
      ['One file that opens anywhere', 'Images and fonts are embedded, so the result works offline in any browser.'],
      ['Or a clean folder', 'Prefer to edit? Get index.html with its images and CSS as separate files in a ZIP.'],
      ['Private by design', 'Conversion happens on your device. The archive never leaves it.'],
    ],
    faq: [
      ['Will links still work after converting?', 'Links to other websites keep working. Links to pages that were not saved in the archive will point to the original site.'],
      ['Is there a file size limit?', 'Only your device’s memory. Archives of a few hundred megabytes convert fine on a laptop.'],
      ['Can I convert several files at once?', 'Open them one after another; each becomes its own HTML file.'],
    ],
    related: ['mhtml-viewer', 'webarchive-viewer', 'zip-html-viewer'],
  },
  {
    slug: 'eml-viewer',
    nav: 'EML viewer',
    title: 'Open EML Files Online — Read Email Files Without Outlook | HTML Lens',
    description: 'Read .eml email files in your browser with images, sender details and attachments. No Outlook or Mail app needed. Private: nothing is uploaded.',
    kicker: 'EML file viewer',
    h1: 'Read .eml email files without an email app.',
    intro: 'Drop an .eml file to see the message as it was sent: subject, sender, recipients, the formatted body with inline images, and a list of attachments you can save.',
    accept: '.eml',
    steps: [
      ['Choose the .eml file', 'Exported from Outlook, Thunderbird, Gmail or an archive.'],
      ['Read it safely', 'Emails open in Safe mode: no scripts and no remote images, like a careful mail client.'],
      ['Save attachments', 'Download any attachment, or check the message for phishing first.'],
    ],
    features: [
      ['Headers at a glance', 'From, To, Cc, date and subject, decoded properly in any language.'],
      ['Inline images restored', 'Pictures embedded in the email show in place.'],
      ['Phishing check included', 'The same scanner that catches fake sign-in pages checks the message body.'],
      ['Test your own HTML emails', 'See dark mode, blocked images and Gmail’s clipping limit before you send.'],
    ],
    faq: [
      ['What is an .eml file?', 'A single email saved as a file — the standard format used by most email programs when you save or export a message.'],
      ['Do remote images load?', 'Not by default, so senders can’t tell that you opened the message. Switch to Online mode if you want them.'],
      ['Can I open .msg files from Outlook?', 'Not yet. In Outlook, use File › Save As and choose the .eml format, or forward the message to yourself.'],
    ],
    related: ['html-email-tester', 'html-attachment-checker', 'html-viewer'],
  },
  {
    slug: 'webarchive-viewer',
    nav: 'Web Archive viewer',
    title: 'Open Safari .webarchive Files on Windows, Android & Chrome | HTML Lens',
    description: 'Open Safari .webarchive files on Windows, Android, Linux or in Chrome. View the saved page with images, or convert it to HTML. Free and private.',
    kicker: 'Safari Web Archive viewer',
    h1: 'Open Safari web archives anywhere.',
    intro: 'Safari saves pages as .webarchive files that only Safari understands. Drop one here to read it on Windows, Android or any browser — and convert it to normal HTML if you like.',
    accept: '.webarchive',
    steps: [
      ['Choose the .webarchive', 'Binary and XML web archives are both supported.'],
      ['It’s rebuilt as a page', 'Images, styles and frames saved inside are restored.'],
      ['Keep it as HTML', 'Save a single .html file that any browser can open.'],
    ],
    features: [
      ['No Mac required', 'Works on Windows, Android, Linux and ChromeOS.'],
      ['Frames included', 'Pages with embedded frames are restored with them.'],
      ['Convert in one step', 'Export as a self-contained HTML file or a ZIP.'],
    ],
    faq: [
      ['Why won’t Chrome open .webarchive files?', 'It’s an Apple-only format based on property lists. HTML Lens reads that format directly.'],
      ['Is anything sent to a server?', 'No. The archive is unpacked in your browser.'],
    ],
    related: ['mhtml-viewer', 'mhtml-to-html', 'html-viewer'],
  },
  {
    slug: 'zip-html-viewer',
    nav: 'ZIP & folder viewer',
    title: 'View an HTML Website from a ZIP File — No Unzipping | HTML Lens',
    description: 'Open a ZIP of a website, report or export and browse it with working links, images and CSS. Great for test reports, documentation and data exports.',
    kicker: 'ZIP and folder viewer',
    h1: 'Browse a website straight out of a ZIP.',
    intro: 'Reports, documentation, data exports and saved sites usually come as a ZIP full of pages. Drop it here and click around as if it were online — links, images and CSS all work.',
    accept: '.zip',
    steps: [
      ['Drop the ZIP', 'No need to unzip it first.'],
      ['The start page opens', 'HTML Lens finds index.html (or the right entry page) automatically.'],
      ['Search every page', 'Find text across all pages at once, and jump straight to it.'],
    ],
    features: [
      ['Recognises common exports', 'Telegram chat exports, Facebook and Instagram data downloads, Google Takeout, JaCoCo and Gradle test reports, Javadoc.'],
      ['Search across all pages', 'Look for a word in every file of the export, with snippets and jump links.'],
      ['File browser', 'See every file in the archive and open any page directly.'],
    ],
    faq: [
      ['Are encrypted ZIP files supported?', 'Not yet. Encrypted entries are skipped with a warning.'],
      ['Can I open a folder instead?', 'Yes, in the web app (Open a folder) and in the desktop app.'],
      ['How big can the ZIP be?', 'Hundreds of megabytes work on a laptop; phones have less memory.'],
    ],
    related: ['telegram-export-viewer', 'html-viewer', 'mhtml-viewer'],
  },
  {
    slug: 'html-attachment-checker',
    nav: 'Attachment safety check',
    title: 'Is This HTML Attachment Safe? Free Phishing Checker | HTML Lens',
    description: 'Check an .html or .htm email attachment for phishing before you open it. Detects fake sign-in pages, password theft, HTML smuggling and ClickFix lures. Runs on your device.',
    kicker: 'HTML attachment safety check',
    h1: 'Got an HTML attachment? Check it before you open it.',
    intro: 'Attachments ending in .html or .htm are one of the most common ways to steal passwords. Drop the file here: it’s analysed and shown in a locked-down sandbox, never opened normally.',
    accept: '.html,.htm,.shtml,.eml,.svg',
    steps: [
      ['Drop the attachment', 'Save it from your email first — don’t double-click it.'],
      ['Read the verdict', 'You get a plain-language report: what the file tries to do, and how risky it is.'],
      ['Look without risk', 'Dangerous files open in Safe mode, with scripts and internet access switched off.'],
    ],
    features: [
      ['Fake sign-in pages', 'Microsoft 365, Google, banks, DocuSign, Adobe and other look-alikes.'],
      ['Password and card theft', 'Forms that send what you type to someone else’s server.'],
      ['Data sent to bots', 'Telegram bots, Discord webhooks and request-capture services used by phishing kits.'],
      ['HTML smuggling and ClickFix', 'Pages that build malware downloads, or tell you to paste a command into Run.'],
    ],
    faq: [
      ['Why are HTML attachments dangerous?', 'They open in your browser, so they can show a convincing sign-in page that runs entirely from your computer — no suspicious link to spot.'],
      ['Does a clean result mean the file is safe?', 'It means none of the known tricks were found. If you didn’t expect the email, still don’t type a password into the page.'],
      ['Is the file sent to a virus scanner?', 'No. The check runs on your device, so confidential attachments stay confidential.'],
    ],
    related: ['eml-viewer', 'html-viewer', 'zip-html-viewer'],
  },
  {
    slug: 'run-html-code',
    nav: 'Run HTML code',
    title: 'Run HTML Code Online — Preview Code from ChatGPT or Claude | HTML Lens',
    description: 'Paste HTML, CSS and JavaScript and see it run instantly. Perfect for code from ChatGPT, Claude or Gemini. Save it as an app, share it with a link. Free.',
    kicker: 'Run HTML code',
    h1: 'Paste HTML and see it run.',
    intro: 'Asked an AI for a calculator, a game or a landing page? Paste the code below and it runs straight away — then keep it as an app on your device or share it with a link.',
    accept: '.html,.htm',
    paste: true,
    steps: [
      ['Paste the code', 'A full page or just a snippet. CSS and JavaScript inside work too.'],
      ['It runs immediately', 'In a sandbox, so nothing it does can touch your other data.'],
      ['Keep or share it', 'Save it as an app with version history, or copy a link that contains the page.'],
    ],
    features: [
      ['A real code editor', 'Syntax highlighting, auto-closing tags, Emmet abbreviations and live preview.'],
      ['Keep it as an app', 'Each app has its own storage, so to-do lists and game scores are remembered.'],
      ['Share without a server', 'The page is compressed into the link itself. Nothing is uploaded or stored.'],
      ['Phone, tablet and desktop previews', 'Check responsive layouts side by side.'],
    ],
    faq: [
      ['Does the code need internet access?', 'Only if it loads libraries from a CDN, such as Tailwind or Chart.js. Pasted code runs in Online mode by default for that reason; you can switch to Offline.'],
      ['How do share links work without a server?', 'The page is compressed and stored after the # in the link. Browsers never send that part to any server.'],
      ['Can I edit the code afterwards?', 'Yes. Open it in the editor; every save keeps a version you can compare or restore.'],
    ],
    related: ['html-viewer', 'html-email-tester', 'zip-html-viewer'],
  },
  {
    slug: 'html-email-tester',
    nav: 'HTML email tester',
    title: 'HTML Email Tester — Gmail Clipping, Dark Mode & Outlook Checks | HTML Lens',
    description: 'Test an HTML email before you send it: Gmail’s 102 KB clipping limit, dark mode inversion, blocked images, Outlook layout problems and missing alt text. Free.',
    kicker: 'HTML email tester',
    h1: 'Check your HTML email before it goes out.',
    intro: 'Drop the email’s .html (or an .eml) to preview it at desktop and phone widths, in dark mode and with images blocked — plus a checklist of what Gmail and Outlook will break.',
    accept: '.html,.htm,.eml',
    tool: 'email',
    steps: [
      ['Open the email HTML', 'From your editor, ESP export or a saved .eml.'],
      ['Preview the tricky cases', 'Phone width, dark mode inversion and images off.'],
      ['Fix what the checks find', 'Each warning says which clients are affected and why.'],
    ],
    features: [
      ['Gmail clipping', 'Warns when the HTML is over 102 KB and Gmail would hide the rest.'],
      ['Dark mode preview', 'Simulates clients that invert colours, or applies your prefers-color-scheme styles.'],
      ['Outlook problems', 'Flexbox, grid, background images, SVG and positioning that Outlook ignores.'],
      ['Accessibility basics', 'Images without alt text, empty links and missing titles.'],
    ],
    faq: [
      ['Does this send test emails?', 'No. Everything is a local preview. It complements, rather than replaces, sending a real test to yourself.'],
      ['Why are my images not showing in the preview?', 'Images with relative paths won’t load in real inboxes either. Use absolute https:// URLs.'],
    ],
    related: ['eml-viewer', 'run-html-code', 'html-viewer'],
  },
  {
    slug: 'telegram-export-viewer',
    nav: 'Telegram export viewer',
    title: 'Open a Telegram Chat Export on Your Phone or Computer | HTML Lens',
    description: 'Read and search a Telegram Desktop chat export (messages.html) on any device. Search every message across all pages and jump straight to it. Private.',
    kicker: 'Telegram chat export viewer',
    h1: 'Read and search your Telegram chat export.',
    intro: 'Telegram Desktop exports chats as a folder of messages.html pages. Zip that folder and drop it here to read it on any device and search every message at once.',
    accept: '.zip',
    steps: [
      ['Zip the export folder', 'The folder Telegram created, with messages.html, photos and css inside.'],
      ['Drop the ZIP', 'HTML Lens recognises it as a Telegram export.'],
      ['Search everything', 'Type a word to find every message across all pages, then jump to it.'],
    ],
    features: [
      ['All pages, one search', 'messages.html, messages2.html, messages3.html… searched together.'],
      ['Photos and stickers load', 'Media saved in the export shows in place.'],
      ['Stays on your device', 'Private conversations are never uploaded.'],
    ],
    faq: [
      ['How do I export a Telegram chat?', 'In Telegram Desktop, open the chat, click ⋮ › Export chat history, and choose the HTML format.'],
      ['Can I open it on iPhone?', 'Yes. Put the ZIP in Files, then open it here in Safari.'],
    ],
    related: ['zip-html-viewer', 'html-viewer'],
  },
];

export const guides = [
  {
    slug: 'open-html-file-on-iphone',
    title: 'How to Open an HTML File on iPhone or iPad (3 Ways) | HTML Lens',
    description: 'Step-by-step ways to open .html files on iPhone and iPad, including pages with images and HTML email attachments.',
    h1: 'How to open an HTML file on iPhone or iPad',
    body: `
<p>iPhone and iPad can preview simple HTML files, but they often show raw code, miss the images, or refuse to open the file from Mail. Here are three ways that work, from quickest to most complete.</p>
<h2>1. Quick Look in the Files app</h2>
<ol><li>Save the file to <b>Files</b> (in Mail, touch and hold the attachment, then <b>Save to Files</b>).</li><li>Open Files and tap the .html file.</li></ol>
<p>This works for simple pages. Pictures stored in separate files will be missing, scripts won’t run, and you can’t see the source.</p>
<h2>2. Open it in HTML Lens (images, scripts and safety check)</h2>
<ol><li>Open <a href="/app/">htmllens.silentdev.online/app</a> in Safari.</li><li>Tap <b>Open a file</b> and choose the file from Files.</li><li>If the page came with a folder of images, zip the folder in Files (touch and hold › <b>Compress</b>) and open the ZIP instead.</li></ol>
<p>The page renders with its images and styles, and you can view source, search the text and check it for phishing. Nothing is uploaded. Tip: tap Share › <b>Add to Home Screen</b> to keep HTML Lens one tap away.</p>
<h2>3. HTML email attachments</h2>
<p>If an email arrives with an .html or .htm attachment you didn’t expect, don’t open it in Safari. Save it to Files and run it through the <a href="/html-attachment-checker/">attachment safety check</a> first — fake sign-in pages are often sent this way.</p>
<h2>What about MHTML or .webarchive files?</h2>
<p>iOS can’t open these at all. Use the <a href="/mhtml-viewer/">MHTML viewer</a> or the <a href="/webarchive-viewer/">web archive viewer</a>.</p>`,
    related: ['html-viewer', 'mhtml-viewer', 'html-attachment-checker'],
  },
  {
    slug: 'open-html-file-on-android',
    title: 'How to Open an HTML File on Android | HTML Lens',
    description: 'Open .html files on Android phones and tablets — with images, without raw code, and safely for attachments.',
    h1: 'How to open an HTML file on Android',
    body: `
<p>Tapping an .html file on Android often opens it as plain text, or Chrome shows a blank page because it can’t reach the images saved next to it. Here’s how to open it properly.</p>
<h2>Open it with HTML Lens in Chrome</h2>
<ol><li>Open <a href="/app/">htmllens.silentdev.online/app</a> in Chrome.</li><li>Tap <b>Open a file</b> and pick the file from Downloads or your file manager.</li><li>For pages with a folder of images, pick the whole folder with <b>Open a folder</b>, or a ZIP of it.</li></ol>
<p>Chrome can install HTML Lens as an app (menu › <b>Install app</b>), so it appears in your app drawer and works offline.</p>
<h2>Saved pages from Chrome</h2>
<p>Pages saved with “Download page” are stored as .mhtml. Open those with the <a href="/mhtml-viewer/">MHTML viewer</a> to see them with images on any device.</p>
<h2>Attachments from WhatsApp, Telegram or email</h2>
<p>An HTML file from someone you don’t know can imitate a login page. Use the <a href="/html-attachment-checker/">safety check</a> before entering anything.</p>`,
    related: ['html-viewer', 'mhtml-viewer', 'zip-html-viewer'],
  },
  {
    slug: 'open-mhtml-file',
    title: 'How to Open an MHTML (.mht) File on Mac, iPhone, Android or Windows | HTML Lens',
    description: 'What MHTML and MHT files are, and how to open them on every device — even where the browser has no support.',
    h1: 'How to open an MHTML or MHT file on any device',
    body: `
<p>MHTML (.mhtml or .mht) packs a whole web page — text, pictures, styles — into one file. Chrome and Edge create them with <b>Save page as › Webpage, Single File</b>. The trouble starts when you try to open one somewhere else.</p>
<h2>On Windows</h2>
<p>Edge and Chrome open .mhtml files directly: right-click the file › <b>Open with</b> › Edge or Chrome.</p>
<h2>On Mac, iPhone and iPad</h2>
<p>Safari doesn’t support MHTML. Open the file in the <a href="/mhtml-viewer/">HTML Lens MHTML viewer</a> instead: it unpacks the archive in your browser and shows the page with its images. Nothing is uploaded.</p>
<h2>On Android</h2>
<p>Chrome on Android only opens MHTML files it saved itself, from its Downloads screen. For files from anywhere else, use the <a href="/mhtml-viewer/">MHTML viewer</a>.</p>
<h2>Turn it into normal HTML</h2>
<p>If you want a file that opens everywhere, convert it with <a href="/mhtml-to-html/">MHTML to HTML</a>: you get one .html file with the images embedded.</p>`,
    related: ['mhtml-viewer', 'mhtml-to-html', 'webarchive-viewer'],
  },
];

export const homeFaq = [
  ['What can HTML Lens open?', 'HTML and HTM pages, ZIP files and folders of websites, MHTML and MHT archives, Safari .webarchive files, .eml email files and SVG images.'],
  ['Are my files uploaded?', 'No. Files are read and rendered by your own browser or the desktop app. There is no server that receives them.'],
  ['Is it safe to open a suspicious attachment here?', 'It’s much safer than double-clicking it. Every file is checked for phishing tricks, and dangerous files open in Safe mode with scripts and internet access switched off.'],
  ['Does it work offline?', 'Yes. Once loaded, the web app keeps working without a connection, and the desktop app never needs one.'],
  ['Is there a Mac or Windows app?', 'Yes — the desktop app adds file associations, live reload while you edit and a native print to PDF. See the download page.'],
];
