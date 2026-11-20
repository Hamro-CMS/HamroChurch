import type { Themes } from "../../../../types/Settings"

export const defaultThemes: { [key: string]: Themes } = {
    default: {
        name: "default",
        default: true,
        font: {
            family: "",
            size: "1em"
        },
        colors: {
            primary: "#101B30",
            "primary-lighter": "#1C2F4F",
            "primary-darker": "#0B1324",
            "primary-darkest": "#070D19",
            text: "#EEF6FF",
            textInvert: "#06101F",
            "secondary-text": "#FFFFFF",
            secondary: "#0EA5E9",
            "secondary-opacity": "rgba(14, 165, 233, 0.35)",
            hover: "rgb(56 189 248 / 0.09)",
            focus: "rgb(56 189 248 / 0.18)"
        }
    },
    dark: {
        name: "dark",
        default: true,
        font: {
            family: "monospace",
            size: "1.1em"
        },
        colors: {
            primary: "#131D31",
            "primary-lighter": "#213252",
            "primary-darker": "#0C1424",
            "primary-darkest": "#070C17",
            text: "#F0F6FF",
            textInvert: "#06101F",
            "secondary-text": "#FFFFFF",
            secondary: "#F59E0B",
            "secondary-opacity": "rgb(245 158 11 / 0.38)",
            hover: "rgb(56 189 248 / 0.08)",
            focus: "rgb(56 189 248 / 0.16)"
        }
    },
    light: {
        name: "light",
        default: true,
        font: {
            family: "",
            size: "1em"
        },
        colors: {
            primary: "#E2E8F0",
            "primary-lighter": "#CBD5E1",
            "primary-darker": "#F1F5F9",
            "primary-darkest": "#F8FAFC",
            text: "#0F172A",
            textInvert: "#F8FAFC",
            "secondary-text": "#FFFFFF",
            secondary: "#0284C7",
            "secondary-opacity": "rgb(2 132 199 / 0.32)",
            hover: "rgb(15 23 42 / 0.05)",
            focus: "rgb(15 23 42 / 0.1)"
        }
    },
    white: {
        name: "white",
        default: true,
        font: {
            family: "Trebuchet MS",
            size: "1em"
        },
        colors: {
            primary: "#242832",
            "primary-lighter": "#2f3542",
            "primary-darker": "#191923",
            "primary-darkest": "#12121c",
            text: "#f0f0ff",
            textInvert: "#131313",
            "secondary-text": "#ffffff",
            secondary: "#fbe1fe",
            "secondary-opacity": "rgba(251, 225, 254, 0.5)",
            hover: "rgb(255 255 255 / 0.05)",
            focus: "rgb(255 255 255 / 0.1)"
        }
    },
    black: {
        name: "black",
        default: true,
        font: {
            family: "",
            size: "1em"
        },
        colors: {
            primary: "#000000",
            "primary-lighter": "#424242",
            "primary-darker": "#000000",
            "primary-darkest": "#212121",
            text: "#FFFFFF",
            textInvert: "#131313",
            "secondary-text": "#FFFFFF",
            secondary: "#FF2E2E",
            "secondary-opacity": "rgb(255 46 46 / 0.5)",
            hover: "rgb(255 255 255 / 0.2)",
            focus: "rgb(255 255 255 / 0.3)"
        }
    },
    blue: {
        name: "Blue",
        font: {
            family: "Trebuchet MS",
            size: "1em"
        },
        colors: {
            primary: "#323743",
            "primary-lighter": "#454C5E",
            "primary-darker": "#23242F",
            "primary-darkest": "#191929",
            text: "#F0F6FF",
            textInvert: "#404154",
            "secondary-text": "#F0F6FF",
            secondary: "#338BFF",
            "secondary-opacity": "rgba(51, 139, 255, 0.5)",
            hover: "rgb(255 255 255 / 0.05)",
            focus: "rgb(255 255 255 / 0.1)"
        }
    },
    sea: {
        name: "Sea",
        font: {
            family: "Georgia",
            size: "1em"
        },
        colors: {
            primary: "#D5DFF1",
            "primary-lighter": "#BFCBE3",
            "primary-darker": "#E0E5FF",
            "primary-darkest": "#ECF4FE",
            text: "#424861",
            textInvert: "#f0f0ff",
            "secondary-text": "#424861",
            secondary: "#007DB3",
            "secondary-opacity": "rgb(0 125 179 / 0.5)",
            hover: "rgb(0 0 0 / 0.05)",
            focus: "rgb(0 0 0 / 0.1)"
        }
    },
    aqua: {
        name: "Aqua",
        font: {
            family: "sans-serif",
            size: "1em"
        },
        colors: {
            primary: "#202020",
            "primary-lighter": "#303030",
            "primary-darker": "#101010",
            "primary-darkest": "#000000",
            text: "#cccccc",
            textInvert: "#131313",
            "secondary-text": "#eeeeee",
            secondary: "#00ffbe",
            "secondary-opacity": "rgb(0 255 190 / 0.5)",
            hover: "rgb(255 255 255 / 0.2)",
            focus: "rgb(255 255 255 / 0.3)"
        }
    },
    charcoal: {
        name: "Charcoal",
        font: {
            family: "",
            size: "1em"
        },
        colors: {
            primary: "#292929",
            "primary-lighter": "#303030",
            "primary-darker": "#1e1e1e",
            "primary-darkest": "#121212",
            text: "#d0d0d0",
            textInvert: "#131313",
            "secondary-text": "#808080",
            secondary: "#8f8f8f",
            "secondary-opacity": "rgb(143 143 143 / 0.5)",
            hover: "rgb(208 208 208 / 0.05)",
            focus: "rgb(208 208 208 / 0.1)"
        }
    },
    papyrus: {
        name: "Papyrus",
        font: {
            family: "Papyrus",
            size: "0.9em"
        },
        colors: {
            primary: "#EAC6A4",
            "primary-lighter": "#D6B59A",
            "primary-darker": "#FFD1B3",
            "primary-darkest": "#D7B99D",
            text: "#000242",
            textInvert: "#000242",
            "secondary-text": "#000242",
            secondary: "#000361",
            "secondary-opacity": "rgb(0 3 97 / 0.5)",
            hover: "rgb(234 198 164 / 0.2)",
            focus: "rgb(234 198 164 / 0.3)"
        }
    },
    terminal: {
        name: "Terminal",
        font: {
            family: "monospace",
            size: "1em"
        },
        colors: {
            primary: "#202020",
            "primary-lighter": "#303030",
            "primary-darker": "#101010",
            "primary-darkest": "#000000",
            text: "#cccccc",
            textInvert: "#131313",
            "secondary-text": "#F1F1F1",
            secondary: "#00FF00",
            "secondary-opacity": "rgb(58 210 255 / 0.5)",
            hover: "rgb(255 255 255 / 0.2)",
            focus: "rgb(255 255 255 / 0.3)"
        }
    }
}
