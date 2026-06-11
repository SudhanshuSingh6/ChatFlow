import logo from "./logo.svg";
import type { ReactNode } from "react";

interface AuthLayoutProps {
  children: ReactNode;
}

export default function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="min-h-screen flex">
      <div className="hidden md:flex w-1/2 items-center justify-center">
        <img src={logo} alt="ChatFlow" className="w-130 h-130" />
      </div>

      <div className="w-full md:w-1/2 flex items-center justify-center">
        {children}
      </div>
    </div>
  );
}
