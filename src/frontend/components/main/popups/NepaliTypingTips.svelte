<script lang="ts">
    import { nepaliTypingTips } from "../../../utils/nepaliTyping"
    import { language } from "../../../stores"
    import T from "../../helpers/T.svelte"

    const sections = Object.entries(nepaliTypingTips) as [keyof typeof nepaliTypingTips, string[]][]

    function titleFor(section: keyof typeof nepaliTypingTips) {
        const labels = {
            vowels: $language === "ne" ? "स्वर" : "Vowels",
            consonants: $language === "ne" ? "व्यञ्जन" : "Consonants",
            conjuncts: $language === "ne" ? "संयुक्त अक्षर" : "Conjuncts",
            punctuation: $language === "ne" ? "विरामचिन्ह र अंक" : "Punctuation and digits",
            examples: $language === "ne" ? "उदाहरण" : "Examples"
        }

        return labels[section]
    }
    $: isNe = $language === "ne"
    $: modeTips = isNe
        ? ["NE (नेपाली टाइपिङ सक्रिय): रोमनमा शब्द टाइप गर्नुहोस् (जस्तै: yeshu, parmeshwar, namaste) → मुनि नेपाली शब्दहरूको सूची देखिन्छ।", "EN (अंग्रेजी मोड): सामान्य अंग्रेजी टाइप गर्न तल दायाँको EN/NE बटन क्लिक गर्नुहोस् वा Ctrl+Alt+N थिच्नुहोस्।", "किबोर्ड सर्टकटहरू: Space = पहिलो शब्द छान्ने, 1–5 वा ▲/▼ = अर्को विकल्प छान्ने, Enter/Tab = स्पेस नराखी शब्द छान्ने, Esc = अंग्रेजी शब्द जस्ताको तस्तै राख्ने।", "अफलाइन सपोर्ट: इन्टरनेट नहुँदा पनि चर्चका शब्दकोश र फोनेटिक सुझावहरू स्वतः काम गर्छन्।"]
        : ["NE (Nepali Typing ON): Type a Roman word (e.g. yeshu, parmeshwar, namaste) to see Nepali word suggestions.", "EN (English Mode): Click the EN/NE toggle in the bottom-right corner or press Ctrl+Alt+N anytime to switch between EN and NE.", "Suggestion Keys: Space = pick top word, 1–5 or ▲/▼ = pick another suggestion, Enter/Tab = commit without trailing space, Esc = keep English word.", "Offline Ready: Automatically falls back to built-in worship vocabulary & phonetic suggestions when offline."]
</script>

<div class="tips">
    <p class="intro"><T id="typing.popup_intro" /></p>

    <section class="card">
        <h3>{isNe ? "टाइपिङ मोडहरू (EN / NE)" : "Typing Modes (EN / NE)"}</h3>
        <ul>
            {#each modeTips as tip}
                <li>{tip}</li>
            {/each}
        </ul>
    </section>

    {#each sections as [section, entries]}
        <section class="card">
            <h3>{titleFor(section)}</h3>
            <ul>
                {#each entries as entry}
                    <li>{entry}</li>
                {/each}
            </ul>
        </section>
    {/each}
</div>

<style>
    .tips {
        display: grid;
        gap: 1rem;
        min-width: min(980px, 100%);
    }

    .intro {
        margin: 0;
        opacity: 0.82;
        white-space: normal;
        overflow: visible;
        text-overflow: unset;
    }

    .card {
        background: color-mix(in srgb, var(--primary-darkest) 85%, black 15%);
        border: 1px solid var(--primary-lighter);
        border-radius: 14px;
        padding: 1rem 1.15rem;
    }

    h3 {
        margin: 0 0 0.75rem;
        color: var(--secondary);
    }

    ul {
        margin: 0;
        padding-left: 1.15rem;
        display: grid;
        gap: 0.45rem;
    }

    li {
        line-height: 1.5;
    }
</style>
