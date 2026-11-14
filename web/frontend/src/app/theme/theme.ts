import { createTheme } from "@mantine/core";
export const theme = createTheme({
  primaryColor: "indigo",
  primaryShade: { light: 6, dark: 5 },
  defaultRadius: "md",
  fontFamily:
    "Inter, ui-sans-serif, system-ui, Segoe UI, Roboto, Helvetica, Arial, sans-serif",
  headings: {
    fontFamily:
      "Inter, ui-sans-serif, system-ui, Segoe UI, Roboto, Helvetica, Arial, sans-serif",
    fontWeight: "800",
  },
  components: {
    Button: { defaultProps: { fw: 650, radius: "md" } },
    Paper: { defaultProps: { withBorder: true, radius: "lg" } },
    Badge: { defaultProps: { radius: "sm", fw: 700 } },
    Table: { defaultProps: { horizontalSpacing: "md" } },
    Modal: {
      defaultProps: { radius: "lg", centered: true, overlayProps: { blur: 3 } },
    },
    TextInput: { defaultProps: { radius: "md" } },
    Select: { defaultProps: { radius: "md" } },
    Textarea: { defaultProps: { radius: "md" } },
    PasswordInput: { defaultProps: { radius: "md" } },
    SegmentedControl: { defaultProps: { radius: "md" } },
  },
});
