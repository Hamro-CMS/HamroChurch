<script lang="ts">
    import { language } from "../../../stores"
    import { getActiveCategoryId, getCategoryCounts, getHymnCategoryDisplay, hymnItems, hymnSelectedCategories, toggleCategory, type HymnCategoryId } from "./hymns"
    import T from "../../helpers/T.svelte"

    $: counts = getCategoryCounts($hymnItems)

    const categoryOrder: { id: HymnCategoryId; icon: string }[] = [
        { id: "all", icon: "●" },
        { id: "bhajan", icon: "◫" },
        { id: "chorus", icon: "◫" },
        { id: "children", icon: "◫" },
        { id: "new", icon: "◫" }
    ]

    function isChecked(id: HymnCategoryId) {
        return getActiveCategoryId($hymnSelectedCategories) === id
    }
</script>

<div class="sidebar">
    <button class="item allItem" class:active={isChecked("all")} on:click={() => toggleCategory("all")}>
        <span class="icon">●</span>
        <span class="label">{getHymnCategoryDisplay("all", $language)}</span>
        <span class="count">{counts.all}</span>
    </button>

    <div class="sectionLabel">
        <T id="hymns.categories_heading" />
    </div>

    <div class="filters">
        {#each categoryOrder.filter((category) => category.id !== "all") as category}
            <button class="item categoryItem" class:active={isChecked(category.id)} on:click={() => toggleCategory(category.id)}>
                <span class="icon">{category.icon}</span>
                <span class="label">{getHymnCategoryDisplay(category.id, $language)}</span>
                <span class="count">{counts[category.id]}</span>
            </button>
        {/each}
    </div>
</div>

<style>
    .sidebar {
        display: flex;
        flex-direction: column;
        gap: 0.14rem;
        padding: 0.3rem 0 0.2rem;
        background: var(--primary-darker);
        height: 100%;
        min-height: 0;
        box-sizing: border-box;
    }

    .sectionLabel {
        padding: 0.14rem 0.85rem 0.06rem;
        font-size: 0.69rem;
        font-weight: 600;
        opacity: 0.68;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        flex-shrink: 0;
    }

    .filters {
        display: flex;
        flex-direction: column;
        gap: 2px;
        overflow-y: auto;
        min-height: 0;
        padding-right: 0.25rem;
    }

    .item {
        display: grid;
        grid-template-columns: 0.95rem 1fr auto;
        align-items: center;
        gap: 0.52rem;
        padding: 0.34rem 0.85rem;
        font-size: 0.85rem;
        color: var(--text);
        cursor: pointer;
        border: none;
        border-left: 3px solid transparent;
        background: transparent;
        text-align: left;
        flex-shrink: 0;
    }

    .item:hover,
    .item.active {
        background: var(--hover);
    }

    .item.active {
        background: color-mix(in srgb, var(--secondary) 14%, var(--primary-darker) 86%);
        border-left-color: var(--secondary);
    }

    .categoryItem.active {
        background: linear-gradient(90deg, color-mix(in srgb, #ff4d4f 30%, var(--primary-darker) 70%) 0%, color-mix(in srgb, #ff8a00 18%, var(--primary-darker) 82%) 100%);
        border-left-color: #ff5a5d;
        box-shadow: inset 0 0 0 1px color-mix(in srgb, #ff5a5d 62%, transparent 38%), 0 0 0 1px color-mix(in srgb, #ff8a00 25%, transparent 75%);
    }

    .categoryItem.active .label {
        font-weight: 700;
    }

    .categoryItem.active .count,
    .categoryItem.active .icon {
        color: #ffd5a6;
        opacity: 1;
    }

    .icon {
        font-size: 0.88rem;
        line-height: 1;
        opacity: 0.8;
    }

    .label {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        line-height: 1.2;
    }

    .count {
        font-size: 0.76rem;
        opacity: 0.68;
    }

    .allItem {
        margin-bottom: 0.05rem;
    }
</style>