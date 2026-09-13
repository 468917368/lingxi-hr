/// <reference types="umi/client" />

declare module '*.less' {
  const classes: { [key: string]: string };
  export default classes;
}
