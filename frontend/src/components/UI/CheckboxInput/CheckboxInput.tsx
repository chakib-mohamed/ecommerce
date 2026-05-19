import React from "react";

interface CheckboxInputProps {
  name: string;
  onChangeHandler: (e: React.ChangeEvent<HTMLInputElement>) => void;
  isValid?: boolean;
  className?: string;
  label?: string;
}

const CheckboxInput: React.FC<CheckboxInputProps> = ({
  name,
  onChangeHandler,
  className = "",
  label
}) => {
  return (
    <label className={`inline-flex items-center space-x-3 cursor-pointer group ${className}`}>
      <div className="relative flex items-center justify-center">
        <input
          id={name}
          name={name}
          type="checkbox"
          onChange={onChangeHandler}
          className="peer appearance-none w-6 h-6 border-2 border-slate-200 rounded-lg bg-white checked:bg-blue-600 checked:border-blue-600 transition-all cursor-pointer shadow-sm group-hover:border-blue-400"
        />
        <i className="fa fa-check absolute text-white scale-0 peer-checked:scale-100 transition-transform pointer-events-none text-[10px]"></i>
      </div>
      {label && (
        <span className="text-sm font-bold text-slate-700 group-hover:text-blue-600 transition-colors italic uppercase tracking-widest text-[10px]">
          {label}
        </span>
      )}
    </label>
  );
};

export default React.memo(CheckboxInput);
