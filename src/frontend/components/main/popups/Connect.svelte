<script lang="ts">
    import qrcode from "qrcode-generator"
    import { onMount } from "svelte"
    import { Main } from "../../../../types/IPC/Main"
    import { AudioAnalyser } from "../../../audio/audioAnalyser"
    import { requestMain, sendMain } from "../../../IPC/main"
    import { activePopup, companion, maxConnections, mobileRemoteClients, os, outputs, popupData, ports, remotePassword, serverData, special } from "../../../stores"
    import { newToast } from "../../../utils/common"
    import { save } from "../../../utils/save"
    import { clone, keysToID, sortByName } from "../../helpers/array"
    import Icon from "../../helpers/Icon.svelte"
    import T from "../../helpers/T.svelte"
    import Link from "../../inputs/Link.svelte"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import MaterialDropdown from "../../inputs/MaterialDropdown.svelte"
    import MaterialNumberInput from "../../inputs/MaterialNumberInput.svelte"
    import MaterialTextInput from "../../inputs/MaterialTextInput.svelte"
    import MaterialToggleSwitch from "../../inputs/MaterialToggleSwitch.svelte"

    type ConnectId = "remote" | "stage" | "controller" | "output_stream" | "companion" | "mobile"
    let id: ConnectId = "mobile"
    let ip = "localhost"
    let allIps: string[] = []
    let showTokenPlain = false

    $: useHostname = $special.connectionHostname

    onMount(async () => {
        id = ($popupData.id as ConnectId) || "mobile"
        ip = $popupData.ip || "localhost"
        allIps = Array.isArray($popupData.ips) ? $popupData.ips : []
        popupData.set({})

        const freshIps = (await requestMain(Main.IP)) || ["localhost"]
        allIps = freshIps
        ip = freshIps[0] || ip

        const clients = await requestMain(Main.MOBILE_CLIENTS_GET)
        if (Array.isArray(clients)) mobileRemoteClients.set(clients)
    })

    const defaultPorts: Record<ConnectId, number> = {
        remote: 5510,
        stage: 5511,
        controller: 5512,
        output_stream: 5513,
        companion: 5505,
        mobile: 5505
    }

    $: portKey = id === "mobile" ? "companion" : id
    $: port = $ports[portKey] || defaultPorts[id]
    $: hostname = `${($os.name || "church-pc").toLowerCase()}${$os.platform === "win32" ? ".local" : ""}`
    $: url = `http://${useHostname && $os.name ? hostname : ip}:${port}`

    $: lanIps = allIps.filter((addr) => addr && addr !== "localhost" && addr !== "127.0.0.1")
    $: mobileEnabled = $companion?.enabled === true && $companion?.allowLan === true && typeof $companion?.token === "string" && $companion.token.trim().length >= 16

    $: mobileQrPayload = (() => {
        if (!mobileEnabled || !lanIps.length) return ""
        const cleanToken = ($companion?.token || "").trim()
        const primaryIp = lanIps[0]
        const altIps = lanIps.slice(1, 3).join(",")
        const params = [`host=${encodeURIComponent(primaryIp)}`]
        if (port !== 5505) params.push(`port=${port}`)
        params.push(`token=${encodeURIComponent(cleanToken)}`)
        if (altIps) params.push(`alt=${encodeURIComponent(altIps)}`)
        return `aradhanaremote://pair?${params.join("&")}`
    })()

    $: if (id === "mobile") {
        if (mobileEnabled) {
            sendMain(Main.WEBSOCKET_START, { port, allowLan: true, token: ($companion?.token || "").trim() })
        }
        generateMobileQR(mobileQrPayload)
    } else if (url) {
        generateQR(url)
    }

    function mouseup(e: any) {
        if (e.target.closest("a")) activePopup.set(null)
    }

    let qrImg = ""
    let mobileQrImg = ""

    function generateQR(text: string) {
        if ((!useHostname && ip === "localhost") || id === "companion" || id === "mobile") {
            qrImg = ""
            return
        }

        const qr = qrcode(0, "L")
        qr.addData(text)
        qr.make()
        qrImg = qr.createSvgTag({ cellSize: 8, margin: 4, scalable: true })
    }

    function generateMobileQR(payload: string) {
        if (!payload) {
            mobileQrImg = ""
            return
        }

        const qr = qrcode(0, "L")
        qr.addData(payload)
        qr.make()
        mobileQrImg = qr.createSvgTag({ cellSize: 6, margin: 4, scalable: true })
    }

    async function enableOrDisableMobileRemote(enable: boolean) {
        let token = typeof $companion?.token === "string" ? $companion.token.trim() : ""
        if (enable && token.length < 16) {
            token = (await requestMain(Main.MOBILE_GENERATE_TOKEN)) || ""
        }

        const next = {
            ...($companion || {}),
            enabled: enable,
            allowLan: enable ? true : ($companion?.allowLan === true),
            token
        }
        companion.set(next)

        if (enable) {
            sendMain(Main.WEBSOCKET_START, { port, allowLan: true, token })
        } else {
            sendMain(Main.WEBSOCKET_STOP)
        }
        save(false, { autosave: true })
    }

    async function regenerateMobileToken() {
        const token = (await requestMain(Main.MOBILE_GENERATE_TOKEN)) || ""
        if (!token) return
        const next = {
            ...($companion || {}),
            enabled: true,
            allowLan: true,
            token
        }
        companion.set(next)
        sendMain(Main.WEBSOCKET_START, { port, allowLan: true, token })
        save(false, { autosave: true })
        newToast("New security token generated — old devices disconnected")
    }

    async function copyValue(value: string, label: string) {
        if (!value) return
        try {
            await navigator.clipboard.writeText(value)
            newToast(`${label} copied`)
        } catch {
            // ignore clipboard errors
        }
    }

    async function disconnectClient(clientId: string) {
        await requestMain(Main.MOBILE_CLIENT_DISCONNECT, { id: clientId })
        const clients = await requestMain(Main.MOBILE_CLIENTS_GET)
        if (Array.isArray(clients)) mobileRemoteClients.set(clients)
    }

    let options = false

    const RESERVED_PORTS: Record<string, number[]> = {
        nowPlaying: [5502],
        WebSocket: [5505],
        REST: [5506],
        companion: [5505, 5506],
        mobile: [5505, 5506],
        remote: [5510],
        stage: [5511],
        controller: [5512],
        output_stream: [5513]
    }
    $: isReserved = !RESERVED_PORTS[id]?.includes(port) && Object.values(RESERVED_PORTS).flat().includes(port)

    function updatePort(e: any) {
        port = e.detail
        ports.update((a) => {
            a[portKey] = port
            return a
        })

        if ((id === "companion" || id === "mobile") && $companion?.enabled) {
            sendMain(Main.WEBSOCKET_START, { port, allowLan: $companion?.allowLan === true, token: $companion?.token || "" })
        }
    }

    function updateCompanionConfig(key: "allowLan" | "token", value: any) {
        let next = { ...($companion || {}), [key]: value }
        companion.set(next)
        if (next.enabled) {
            sendMain(Main.WEBSOCKET_START, { port: $ports.companion, allowLan: next.allowLan === true, token: next.token || "" })
        }
    }

    const setRemotePassword = (e: any) => remotePassword.set(e.detail)

    // output
    $: outputsList = getList(clone($outputs))
    function getList(outputs: Record<string, any>) {
        let list = keysToID(outputs).filter((a) => a.enabled === true)
        return sortByName(list).map((a) => ({ label: a.name, value: a.id }))
    }

    function toggleAudio(e: any) {
        let value = e.detail
        updateData(value, "sendAudio")
        if (value) AudioAnalyser.recorderActivate()
    }

    function updateData(e: any, key: string) {
        let value = e.detail ?? e

        serverData.update((a) => {
            if (!a[id]) a[id] = {}
            a[id][key] = value

            return a
        })

        sendMain(Main.SERVER_DATA, $serverData)
    }

    function updateSpecial(key: string, value: any) {
        special.update((a) => {
            a[key] = value
            return a
        })
    }
</script>

{#if id !== "mobile"}
    <MaterialButton class="popup-options {options ? 'active' : ''}" icon="options" iconSize={1.3} title={options ? "actions.close" : "create_show.more_options"} on:click={() => (options = !options)} white />
    <MaterialButton class="popup-reset" icon="help" iconSize={1.1} title="titlebar.help" on:click={() => sendMain(Main.URL, "https://hamrocms.com/docs/remote-control/")} white />
{/if}

{#if id === "mobile"}
    <div class="mobileModal">
        <div class="mobileHeader">
            <div class="mobileTitleRow">
                <Icon id="mobile" size={1.4} />
                <div class="mobileTitleText">
                    <h3>Aradhana Studio Mobile Remote (Android)</h3>
                    <p>मोबाइलबाट स्लाइड, भजन र बाइबल नियन्त्रण गर्नुहोस् (इन्टरनेट बिना नै Wi-Fi / Hotspot मा चल्ने)</p>
                </div>
            </div>
            <div class="toggleWrap">
                <MaterialToggleSwitch label="" checked={mobileEnabled} on:change={(e) => enableOrDisableMobileRemote(e.detail)} />
            </div>
        </div>

        {#if !mobileEnabled}
            <div class="disabledCard">
                <p style="margin: 0 0 10px;opacity: 0.88;">Mobile Remote बन्द छ। माथिको टगल वा तलको बटन थिचेर सुरक्षित QR पेयरिङ सुरु गर्नुहोस्।</p>
                <MaterialButton icon="mobile" style="background: var(--secondary);color: #fff;padding: 10px 20px;border-radius: 8px;" on:click={() => enableOrDisableMobileRemote(true)}>
                    Enable Mobile Remote / मोबाइल रिमोट अन गर्नुहोस्
                </MaterialButton>
            </div>
        {:else}
            <div class="pairingGrid">
                <div class="qrPanel">
                    {#if lanIps.length && mobileQrImg}
                        <div class="qrWhiteFrame">
                            {@html mobileQrImg}
                        </div>
                        <p class="qrCaption">Scan from <b>Aradhana Studio Remote</b></p>
                    {:else}
                        <div class="noNetworkBox">
                            <p style="margin: 0;font-weight: 700;color: #fbbf24;">No Wi-Fi / Hotspot Detected</p>
                            <p style="margin: 6px 0 0;font-size: 0.82em;opacity: 0.85;">Connect this PC to the same Wi-Fi router OR turn on Mobile Hotspot on your phone/laptop, then reopen this window.</p>
                        </div>
                    {/if}
                </div>

                <div class="manualPanel">
                    <div class="ipPortRow">
                        <div class="fieldGroup ipField">
                            <span class="fieldLabel">LAN IP Address (एउटै Wi-Fi / Hotspot)</span>
                            <div class="fieldValueRow">
                                <code class="monoValue">{lanIps[0] || "Not connected"}</code>
                                {#if lanIps[0]}
                                    <MaterialButton style="padding: 5px 7px;min-height: 28px;" icon="copy" title="Copy IP" on:click={() => copyValue(lanIps[0], "IP Address")} />
                                {/if}
                            </div>
                            {#if lanIps.length > 1}
                                <span class="altIps">Other IPs: {lanIps.slice(1).join(", ")}</span>
                            {/if}
                        </div>

                        <div class="fieldGroup portField">
                            <span class="fieldLabel">Port</span>
                            <div class="fieldValueRow" style="min-height: 28px;">
                                <code class="monoValue">{port} / {port + 1}</code>
                            </div>
                        </div>
                    </div>

                    <div class="fieldGroup">
                        <span class="fieldLabel">Security Access Token</span>
                        <div class="fieldValueRow">
                            <code class="monoValue tokenText">{showTokenPlain ? $companion?.token : "••••••••••••••••"}</code>
                            <div class="tokenActions">
                                <MaterialButton style="padding: 5px 9px;min-height: 28px;" on:click={() => (showTokenPlain = !showTokenPlain)} title="Show/Hide Token">
                                    <span style="font-size: 0.8em;white-space: nowrap;">{showTokenPlain ? "Hide" : "Show"}</span>
                                </MaterialButton>
                                <MaterialButton style="padding: 5px 7px;min-height: 28px;" icon="copy" title="Copy Token" on:click={() => copyValue($companion?.token || "", "Token")} />
                            </div>
                        </div>
                        <div style="margin-top: 6px;display: flex;justify-content: flex-start;flex-wrap: wrap;">
                            <MaterialButton icon="refresh" style="font-size: 0.8em;padding: 5px 10px;min-height: 28px;border: 1px solid var(--primary-lighter);border-radius: 6px;" on:click={regenerateMobileToken}>
                                Reset Token & Revoke Old Phones
                            </MaterialButton>
                        </div>
                    </div>

                    <div class="connectedSection">
                        <div class="connectedHeader">
                            <span>Connected Phones ({$mobileRemoteClients.length})</span>
                            {#if $mobileRemoteClients.length > 1}
                                <MaterialButton style="font-size: 0.78em;padding: 2px 8px;" on:click={() => disconnectClient("all")}>Disconnect All</MaterialButton>
                            {/if}
                        </div>
                        {#if $mobileRemoteClients.length === 0}
                            <p style="margin: 5px 0 0;font-size: 0.8em;opacity: 0.72;text-align: start;line-height: 1.35;">कुनै मोबाइल जोडिएको छैन — फोनमा <b>Aradhana Studio Remote</b> खोलेर बाँयाको QR स्क्यान गर्नुहोस्।</p>
                        {:else}
                            <div class="clientList">
                                {#each $mobileRemoteClients as client (client.id)}
                                    <div class="clientRow">
                                        <div style="display: flex;align-items: center;gap: 8px;min-width: 0;">
                                            <span class="onlineDot" />
                                            <b style="font-size: 0.86em;overflow: hidden;text-overflow: ellipsis;white-space: nowrap;">{client.deviceName}</b>
                                            <code style="font-size: 0.76em;opacity: 0.7;">{client.ip}</code>
                                        </div>
                                        <MaterialButton icon="close" style="padding: 4px;" title="Disconnect" on:click={() => disconnectClient(client.id)} />
                                    </div>
                                {/each}
                            </div>
                        {/if}
                    </div>
                </div>
            </div>

            <p class="firewallHint">
                Tip: मोबाइल र कम्प्युटर एउटै Wi-Fi वा Mobile Hotspot मा जोड्नुहोस् र Windows Firewall मा "Private Network" अनुमति दिनुहोस्।
            </p>
        {/if}
    </div>
{:else if options}
    <div class="reserved" class:isReserved>
        <MaterialNumberInput label="settings.port" value={port} defaultValue={RESERVED_PORTS[id]?.[0]} min={1025} max={65535} on:change={(e) => updatePort(e)} />
    </div>

    {#if id === "remote"}
        <MaterialTextInput label="remote.password" value={$remotePassword} on:change={setRemotePassword} />
    {:else if id === "companion"}
        <MaterialTextInput label="API Token" type="password" disableNepaliTyping value={$companion?.token || ""} placeholder="Minimum 16 characters required for LAN access" on:change={(e) => updateCompanionConfig("token", e.detail)} />
        <MaterialToggleSwitch label="Allow LAN Access" checked={$companion?.allowLan === true} defaultValue={false} on:change={(e) => updateCompanionConfig("allowLan", e.detail)} />
    {:else if id === "output_stream"}
        <MaterialDropdown label="midi.output" options={outputsList} value={$serverData?.output_stream?.outputId || ""} on:change={(e) => updateData(e.detail, "outputId")} allowEmpty />
        <MaterialToggleSwitch label="preview.audio" checked={$serverData?.output_stream?.sendAudio} defaultValue={false} on:change={toggleAudio} />
    {/if}

    {#if id !== "companion"}
        <hr />

        <MaterialNumberInput label="settings.max_connections" value={$maxConnections} max={100} on:change={(e) => maxConnections.set(e.detail)} />
        {#if $os.name}
            <MaterialToggleSwitch label="settings.use_hostname" checked={$special.connectionHostname} defaultValue={false} on:change={(e) => updateSpecial("connectionHostname", e.detail)} />
        {/if}
    {/if}
{:else}
    <div on:mouseup={mouseup}>
        {#if id === "companion"}
            <Link url="https://hamrocms.com/docs/remote-control/#api-access">
                API Docs
                <Icon id="launch" white />
            </Link>
            <Link url="https://hamrocms.com/docs/remote-control/#bitfocus-companion">
                Bitfocus Companion Connection
                <Icon id="launch" white />
            </Link>
        {:else}
            <p><T id="settings.connect" />:</p>
            <Link {url}>
                {url}
                <Icon id="launch" white />
            </Link>

            <br />

            {#if ip === "localhost"}
                <p style="text-align: start;">
                    <T id="error.ip" />
                </p>
            {:else if qrImg}
                <p style="margin-bottom: 5px;"><T id="settings.connect_qr" />:</p>
                {@html qrImg}
            {/if}
        {/if}

        {#if id === "remote" && $remotePassword}
            <p style="padding-top: 10px;font-size: 0.9em;"><T id="remote.password" />: <b>{$remotePassword}</b></p>
        {/if}
    </div>
{/if}

<style>
    div {
        text-align: center;
    }

    div :global(a) {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    hr {
        border: none;
        height: 2px;
        margin: 20px 0;
        background-color: var(--primary-lighter);
    }

    div :global(img) {
        border-radius: 4px;
    }

    .reserved.isReserved :global(input) {
        color: #ff3232;
    }

    .mobileModal {
        display: flex;
        flex-direction: column;
        gap: 14px;
        width: min(650px, 82vw);
        max-width: 100%;
        min-width: 0;
        padding: 2px 4px;
        box-sizing: border-box;
        text-align: start;
        font-size: 0.88em;
    }

    .mobileModal p,
    .mobileModal span,
    .mobileModal h3 {
        white-space: normal;
        overflow: visible;
        text-overflow: clip;
    }

    .mobileHeader {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        min-width: 0;
        padding-bottom: 10px;
        border-bottom: 1px solid var(--primary-lighter);
    }

    .mobileTitleRow {
        display: flex;
        align-items: center;
        gap: 10px;
        flex: 1;
        min-width: 0;
    }

    .mobileTitleText {
        text-align: start;
        min-width: 0;
        flex: 1;
    }

    .mobileTitleText h3 {
        margin: 0;
        font-size: 1.06em;
        line-height: 1.25;
    }

    .mobileTitleText p {
        margin: 3px 0 0;
        font-size: 0.82em;
        opacity: 0.76;
        line-height: 1.35;
    }

    .toggleWrap {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        padding-inline-end: 4px;
    }

    .disabledCard {
        background: var(--primary-darker);
        border: 1px solid var(--primary-lighter);
        border-radius: 10px;
        padding: 20px 16px;
        text-align: center;
    }

    .pairingGrid {
        display: grid;
        grid-template-columns: 232px minmax(0, 1fr);
        gap: 18px;
        align-items: stretch;
        min-width: 0;
    }

    .qrPanel {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 10px;
        width: 232px;
        max-width: 100%;
        padding: 14px;
        border-radius: 12px;
        background: var(--primary-darker);
        border: 1px solid color-mix(in srgb, var(--secondary) 32%, var(--primary-lighter) 68%);
        box-sizing: border-box;
        min-width: 0;
    }

    .qrWhiteFrame {
        width: 192px;
        height: 192px;
        background: #ffffff;
        padding: 10px;
        border-radius: 12px;
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.42);
        display: flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;
        flex-shrink: 0;
    }

    .qrWhiteFrame :global(svg),
    .qrWhiteFrame :global(img) {
        width: 100% !important;
        height: 100% !important;
        max-width: 172px;
        max-height: 172px;
        display: block;
        shape-rendering: crispEdges;
        image-rendering: pixelated;
    }

    .qrCaption {
        margin: 0;
        font-size: 0.78em;
        opacity: 0.9;
        text-align: center;
        line-height: 1.35;
    }

    .noNetworkBox {
        width: 100%;
        padding: 12px;
        border-radius: 10px;
        background: rgba(251, 191, 36, 0.1);
        border: 1px solid rgba(251, 191, 36, 0.35);
        text-align: start;
        box-sizing: border-box;
    }

    .manualPanel {
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        gap: 10px;
        text-align: start;
        min-width: 0;
    }

    .ipPortRow {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 128px;
        gap: 10px;
        min-width: 0;
    }

    .fieldGroup {
        display: flex;
        flex-direction: column;
        gap: 4px;
        background: var(--primary-darker);
        padding: 9px 12px;
        border-radius: 10px;
        border: 1px solid var(--primary-lighter);
        min-width: 0;
        box-sizing: border-box;
    }

    .fieldLabel {
        font-size: 0.74em;
        opacity: 0.75;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        line-height: 1.3;
        word-break: break-word;
    }

    .fieldValueRow {
        display: flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
    }

    .monoValue {
        font-family: Consolas, monospace;
        font-size: 0.92em;
        font-weight: 700;
        color: var(--text);
        flex: 1;
        min-width: 0;
        text-align: start;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .tokenText {
        font-size: 0.82em;
    }

    .tokenActions {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        flex-shrink: 0;
    }

    .altIps {
        font-size: 0.75em;
        opacity: 0.68;
        line-height: 1.3;
        word-break: break-word;
    }

    .connectedSection {
        background: var(--primary-darker);
        border: 1px solid var(--primary-lighter);
        border-radius: 8px;
        padding: 10px 12px;
        min-width: 0;
    }

    .connectedHeader {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 0.84em;
        font-weight: 700;
        gap: 8px;
    }

    .clientList {
        display: flex;
        flex-direction: column;
        gap: 6px;
        margin-top: 8px;
    }

    .clientRow {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        padding: 4px 8px;
        border-radius: 6px;
        background: var(--primary-darkest);
        min-width: 0;
    }

    .onlineDot {
        width: 8px;
        height: 8px;
        flex-shrink: 0;
        border-radius: 50%;
        background: #22c55e;
        box-shadow: 0 0 6px #22c55e;
    }

    .firewallHint {
        margin: 0;
        font-size: 0.78em;
        opacity: 0.74;
        text-align: start;
        line-height: 1.4;
        word-break: break-word;
    }

    @media (max-width: 680px) {
        .mobileModal {
            width: 100%;
        }
        .pairingGrid {
            grid-template-columns: 1fr;
        }
        .qrPanel {
            width: 100%;
        }
        .ipPortRow {
            grid-template-columns: 1fr;
        }
        .noNetworkBox {
            max-width: 100%;
        }
    }
</style>
